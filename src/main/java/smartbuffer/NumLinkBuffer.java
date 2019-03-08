package smartbuffer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import util.ObjectLock;
import util.ObjectVN;
import util.Store;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
     * A map from the object to transaction IDS that depend on the object and that the dependency is not resolved.
     */
    private SetMultimap<ObjectVN, Long> unresolveddepsMap;

    /*
     * A map from a transaction ID to the number of unresolved dependencies.
     */
    private HashMap<Long, Integer> numLink;
    
    /*
     * A map from oid to the associated lock.
     */
    private ConcurrentHashMap<Long, Lock> objlocktable;
    
    /*
     * A map from tid to the associated lock
     */
    private ConcurrentHashMap<Long, Lock> txnlocktable;
    
    /*
     * A map from transactions in the buffer to associated futures to be filled.
     */
    private HashMap<Long, CompletableFuture<Boolean>> futures;
    
    /*
     * A pointer to the store that the buffer is associated with.
     */
    public Store store;
    
    
    public NumLinkBuffer() {
        // TODO: decide the implementation we want to use
        // Look at performance considerations, as well as whether we care about value ordering or not
        depsMap = new HashMultimap<>();
        numLink = new HashMap<>();
        objlocktable = new ConcurrentHashMap<>();
        txnlocktable = new ConcurrentHashMap<>();
    }
    
    private Lock getObjLock(Long oid) {
        Lock lock = new ReentrantLock();
        Lock existing = objlocktable.putIfAbsent(oid, lock);
        return existing == null? lock : existing;
    }
    
    private Lock getTxnLock(Long tid) {
        Lock lock = new ReentrantLock();
        Lock existing = objlocktable.putIfAbsent(tid, lock);
        return existing == null? lock : existing;
    }

    @Override
    public Future<Boolean> add(long tid, Set<ObjectVN> deps) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        synchronized (getTxnLock(tid)) {
            numLink.put(tid, 0);
            for (ObjectVN object : deps) {
                synchronized (getObjLock(object.oid)) {
                    if (store.getversion(object.oid) > object.vnum) {
                        //Version Conflict
                        future.complete(false);
                    } else if (store.getversion(object.oid) < object.vnum) {
                        unresolveddepsMap.put(object, tid);
                        depsMap.put(object, tid);
                        numLink.put(tid, numLink.get(tid) + 1);
                    } else {
                        depsMap.put(object, tid);
                    }
                }
            }
            if (numLink.get(tid) == 0) {
                numLink.remove(tid);
                future.complete(store.grabLock(tid));
                futures.remove(tid);
            } else {
                futures.put(tid, future);
            }
        }
        return future;
    }

    @Override
    public void remove(ObjectVN object) {
        eject(object);
        synchronized (getObjLock(object.oid)) {
            //If the current version of object is not ejected from the buffer
            if (unresolveddepsMap.containsKey(object)) {
                for (long tid : unresolveddepsMap.get(object)) {
                    //If [tid] is not ejected from the buffer
                    if (numLink.containsKey(tid)) {
                        synchronized (getTxnLock(tid)) {
                            numLink.put(tid, numLink.get(tid) - 1);
                        }
                        if (numLink.get(tid) == 0) {
                            numLink.remove(tid);
                            futures.get(tid).complete(store.grabLock(tid));
                            futures.remove(tid);
                        }
                    }
                }
                unresolveddepsMap.removeAll(object);
            }
        }
    }

    @Override
    public void eject(ObjectVN object) {
        synchronized (getObjLock(object.oid)) {
            for (ObjectVN object_curr : depsMap.keySet()) {
                if (object_curr.older(object)) {
                    for (long tid : depsMap.get(object_curr)) {
                        synchronized(getTxnLock(tid)) {
                            numLink.remove(tid);
                            futures.get(tid).complete(false);
                            futures.remove(tid);
                        }
                    }
                    depsMap.removeAll(object_curr);
                    unresolveddepsMap.removeAll(object_curr);
                }
            }
        }
    }
    
    @Override
    public void delete(long tid) {
        synchronized (getTxnLock(tid)) {
            numLink.remove(tid);
            futures.remove(tid);
        }
    }

}
