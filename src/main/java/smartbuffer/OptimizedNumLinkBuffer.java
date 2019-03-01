package smartbuffer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import util.ObjectVN;
import util.Store;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class OptimizedNumLinkBuffer implements SmartBuffer {
    /*
     * A map from the object to transaction IDs that depend on the object
     */
    private SetMultimap<ObjectVN, Long> depsMap;

    /*
     * A map from a transaction ID to the number of unresolved dependencies
     */
    private HashMap<Long, Integer> numLink;

    /*
     * A map from the object ID to the version number of the object in the buffer.
     */
    private HashMap<Long, Long> inbufferversion;
    
    /*
     * A pointer to the store that the buffer is associated with.
     */
    public Store store;

    public OptimizedNumLinkBuffer() {
        depsMap = new HashMultimap<>();
        numLink = new HashMap<>();
        inbufferversion = new HashMap<>();
    }

    @Override
    public void add(long tid, Set<ObjectVN> deps) {
        for (ObjectVN object : deps) {
            inbufferversion.put(object.oid, object.vnum);
            depsMap.put(object, tid);
        }
        numLink.put(tid, deps.size());
    }

    @Override
    public List<Long> remove(ObjectVN object) {
        List<Long> translist = new LinkedList<Long>();
        boolean allremoved = true;
        for (long tid : depsMap.get(object)) {
            if (numLink.containsKey(tid)) {
                numLink.put(tid, numLink.get(tid) - 1);
                if (numLink.get(tid) == 0) {
                    translist.add(tid);
                } else {
                    allremoved = false;
                }
            }
        }
        if (allremoved) {
            depsMap.removeAll(object);
        }
        return translist;
    }

    @Override
    public List<Long> eject(ObjectVN object) {
        List<Long> translist = new LinkedList<Long>();
        if (inbufferversion.containsKey(object.oid) && object.vnum > inbufferversion.get(object.oid)) {
            ObjectVN last = new ObjectVN(object.oid, inbufferversion.get(object.oid));
            for (long tid : depsMap.get(last)) {
                if (numLink.containsKey(tid)) {
                    translist.add(tid);
                    numLink.remove(tid);
                }
            }
            depsMap.removeAll(last);
        }
        return translist;
    }
    
    @Override
    public void delete(long tid) {
        numLink.remove(tid);
    }

}
