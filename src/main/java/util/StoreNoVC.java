package util;

import java.util.*;
import smartbuffer.SmartBuffer;

public class StoreNoVC implements Store {
    public SmartBuffer buffer;
    
    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    public HashMap<Long, Long> lastversion;
    
    /*
     * A map from [tid] to transactions in the buffer waiting for processing.
     */
    public HashMap<Long, List<ObjectVN>> pending;
    
    public StoreNoVC(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new HashMap<>();
    }
    
    public void prepare(long tid, List<ObjectVN> write, List<ObjectVN> deps) {
        pending.put(tid, write);
    	boolean depsfulfilled = true;
        Set<ObjectVN> actualdeps = depscheck(deps);
        
        if (!depsfulfilled) {
            buffer.add(tid, actualdeps);
            actualdeps = depscheck(deps);
            if (actualdeps.size() == 0) {
            	commit(tid);
            	buffer.delete(tid);
            }
        } else {
            commit(tid);
    	}
    }
    
    /*
     * Return a set of unresolved dependencies.
     */
    public Set<ObjectVN> depscheck(List<ObjectVN> deps) {
        Set<ObjectVN> actualdeps = new HashSet<>();
        for (ObjectVN object : deps) {
            if (lastversion.get(object.oid) < object.vnum) {
            	actualdeps.add(object);
            }
        }
        return actualdeps;
    }
    
    public void commit(long tid) {
    	for (ObjectVN object : pending.get(tid)) {
    	    lastversion.put(object.oid, object.vnum);
    	    buffer.eject(object);
    	    List<Long> translist = buffer.remove(object);
    	    for (long tid1 : translist) {
    	        commit(tid1);
    	    }
    	}
    	pending.remove(tid);
    }

	@Override
	public void abort(long tid) {
		// TODO Auto-generated method stub
		
	}

}
