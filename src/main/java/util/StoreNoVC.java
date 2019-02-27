package util;

import java.util.*;
import smartbuffer.SmartBuffer;

public class StoreNoVC {
    public SmartBuffer buffer;
    public HashMap<Long, Long> lastversion;
    public HashMap<Long, List<ObjectVN>> pending;
    
    public StoreNoVC(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new HashMap<>();
    }
    
    public void write(long tid, List<ObjectVN> write, List<ObjectVN> deps) {
        pending.put(tid, write);
    	boolean depsfulfilled = true;
        Set<ObjectVN> actualdeps = depscheck(deps);
        
        if (!depsfulfilled) {
            buffer.add(tid, actualdeps);
            actualdeps = depscheck(deps);
            if (actualdeps.size() == 0) {
            	execute(tid);
            	buffer.delete(tid);
            }
        } else {
            execute(tid);
    	}
    }
    
    public Set<ObjectVN> depscheck(List<ObjectVN> deps) {
        Set<ObjectVN> actualdeps = new HashSet<>();
        for (ObjectVN object : deps) {
            if (lastversion.get(object.oid) < object.vnum) {
            	actualdeps.add(object);
            }
        }
        return actualdeps;
    }
    
    public void execute(long tid) {
    	for (ObjectVN object : pending.get(tid)) {
    	    lastversion.put(object.oid, object.vnum);
    	    buffer.eject(object);
    	    List<Long> translist = buffer.remove(object);
    	    for (long tid1 : translist) {
    	        execute(tid1);
    	    }
    	}
    	pending.remove(tid);
    }

}
