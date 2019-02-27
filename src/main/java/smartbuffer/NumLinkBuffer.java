package smartbuffer;

import java.util.*;

import util.ObjectVN;

public class NumLinkBuffer implements SmartBuffer {
    /*
     * A map from object to transactions that depend on the object
     */
    private HashMap<ObjectVN, Set<Long>> depsMap;

    /*
     * A map from a transaction to the number of unremoved deps
     */
    private HashMap<Long, Integer> numLink;

    @Override
    public boolean add(long tid, Set<ObjectVN> deps) {
        for (ObjectVN object : deps) {
            if (depsMap.containsKey(object)){
                depsMap.get(object).add(tid);
            } else {
                Set<Long> l = new HashSet<Long>();
                l.add(tid);
                depsMap.put(object, l);
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
        depsMap.remove(object);
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
                depsMap.remove(object_curr);
            }
        }
        return translist;
    }

}
