package smartbuffer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import util.ObjectVN;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class NumLinkBuffer implements SmartBuffer {
    /*
     * A map from the object to transaction IDs that depend on the object.
     */
    private SetMultimap<ObjectVN, Long> depsMap;

    /*
     * A map from a transaction ID to the number of unresolved dependencies.
     */
    private HashMap<Long, Integer> numLink;
    
    /*
     * A map from oid/vnum pair to the associated lock.
     */
    private HashMap<ObjectVN, Lock> deplocktable;
    
    /*
     * A map from tid to the associated lock
     */
    private HashMap<Long, Lock> txnlocktable;
    
    
    public NumLinkBuffer() {
        // TODO: decide the implementation we want to use
        // Look at performance considerations, as well as whether we care about value ordering or not
        depsMap = new HashMultimap<>();
        numLink = new HashMap<>();
        deplocktable = new HashMap<>();
        txnlocktable = new HashMap<>();
    }

    @Override
    public void add(long tid, Set<ObjectVN> deps) {
        txnlocktable.put(tid, new ReentrantLock());
        synchronized(txnlocktable.get(tid)){
            for (ObjectVN object : deps) {
                if (!deplocktable.containsKey(object)) {
                    deplocktable.put(object, new ReentrantLock());
                }
                // How setmultimap is implemented?
                synchronized(txnlocktable.get(object)) {
                    depsMap.put(object, tid);
                }
            }
            numLink.put(tid, deps.size());
        }
    }

    @Override
    public List<Long> remove(ObjectVN object) {
        List<Long> translist = new LinkedList<Long>();
        for (long tid : depsMap.get(object)) {
            if (numLink.containsKey(tid)) {
                synchronized(txnlocktable.get(tid)) {
                    numLink.put(tid, numLink.get(tid) - 1);
                    if (numLink.get(tid) == 0) {
                        translist.add(tid);
                    }
                }
            }
        }
        depsMap.removeAll(object);
        return translist;
    }

    @Override
    public List<Long> eject(ObjectVN object) {
        List<Long> translist = new LinkedList<Long>();
        for (ObjectVN object_curr : depsMap.keySet()) {
            if (object_curr.older(object)) {
                for (long tid : depsMap.get(object_curr)) {
                    synchronized(txnlocktable.get(tid)){
                        translist.add(tid);
                        numLink.remove(tid);
                    }
                }
                depsMap.removeAll(object_curr);
            }
        }
        return translist;
    }
    
    @Override
    public void delete(long tid) {
        numLink.remove(tid);
    }

}
