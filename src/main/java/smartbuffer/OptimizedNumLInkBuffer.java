package smartbuffer;

import com.google.common.collect.SetMultimap;
import util.ObjectVN;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class OptimizedNumLInkBuffer implements SmartBuffer {
    /*
     * A map from the object to transaction IDs that depend on the object
     */
    private SetMultimap<ObjectVN, Long> depsMap;

    /*
     * A map from a transaction ID to the number of unresolved dependencies
     */
    private HashMap<Long, Integer> numLink;

    /*
     * A map from the object ID to the latest version number that the buffer ever seen
     */
    private HashMap<Long, Long> lastversion;

    @Override
    public boolean add(long tid, Set<ObjectVN> deps) {
        for (ObjectVN object : deps) {
            // If the store has seen this version of object
            if (lastversion.containsKey(object.oid) && object.vnum == lastversion.get(object.oid)) {
                if (depsMap.containsKey(object)) {
                    depsMap.get(object).add(tid);
                } else {
                    depsMap.put(object, tid);
                }
            // If this version of object is newer than the version the buffer has seen
            } else if (lastversion.containsKey(object.oid) && object.vnum > lastversion.get(object.oid)) {
                lastversion.put(object.oid, object.vnum);
                // Eject transactions depend on the last version?
                // eject(object);
                depsMap.put(object, tid);
            } else if (lastversion.containsKey(object.oid) && object.vnum < lastversion.get(object.oid)) {
                return false;
            } else {
                lastversion.put(object.oid, object.vnum);
                depsMap.put(object, tid);
            }
        }
        numLink.put(tid, deps.size());
        return true;
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
        if (lastversion.containsKey(object.oid) && object.vnum > lastversion.get(object.oid)) {
            ObjectVN last = new ObjectVN(object.oid, lastversion.get(object.oid));
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

}
