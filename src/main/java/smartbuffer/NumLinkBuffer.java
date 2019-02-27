package smartbuffer;

import com.google.common.collect.SetMultimap;
import util.ObjectVN;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class NumLinkBuffer implements SmartBuffer {
    /*
     * A map from the object to transaction IDs that depend on the object
     */
    private SetMultimap<ObjectVN, Long> depsMap;

    /*
     * A map from a transaction ID to the number of unresolved dependencies
     */
    private HashMap<Long, Integer> numLink;

    @Override
    public boolean add(long tid, Set<ObjectVN> deps) {
        for (ObjectVN object : deps) {
            if (depsMap.containsKey(object)){
                depsMap.get(object).add(tid);
            } else {
                depsMap.put(object, tid);
            }
        }
        numLink.put(tid, deps.size());
        return true;
    }

    @Override
    public List<Long> remove(ObjectVN object) {
        List<Long> translist = new LinkedList<Long>();
        for (long tid : depsMap.get(object)) {
            if (numLink.containsKey(tid)) {
                numLink.put(tid, numLink.get(tid) - 1);
                if (numLink.get(tid) == 0) {
                    translist.add(tid);
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
                    translist.add(tid);
                    numLink.remove(tid);
                }
                depsMap.removeAll(object_curr);
            }
        }
        return translist;
    }

}
