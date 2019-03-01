package util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.google.common.collect.SetMultimap;

import smartbuffer.SmartBuffer;

public class StoreNoVC implements Store {
    public SmartBuffer buffer;
    
    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    public HashMap<Long, Long> lastversion;
    
    /*
     * A map from [tid] to transactions in waiting for processing.
     */
    public SetMultimap<Long, ObjectVN> pending;
    
    public StoreNoVC(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new SetMultimap<>();
    }
    
    public Future<Boolean> prepare(long tid, Set<ObjectVN> reads, Set<ObjectVN> writes) {
        // Check version conflict
        Set<ObjectVN> actualdeps = new HashSet<>();
        
        for (ObjectVN object : reads) {
            // if there is a older version, there is a version conflict and prepare fails.
            if (object.vnum < lastversion.get(object.oid)) {
                return futurewith(false);
            // if there is a version that's never seen, add that to the dependency of this transaction
            } else if (object.vnum > lastversion.get(object.oid)) {
                actualdeps.add(object);
            }
        }
        
        pending.putAll(tid, writes);
        if (actualdeps.isEmpty()) {
            // TODO Grab the lock on the store's side
            
            // TODO Change the state of [tid] to Prepared
            
            return futurewith(true);
        } else {
            // Result resolved to true if the dependencies of [tid] are resolved. resolved to false only when there is version conflict
            Future<Boolean> result = buffer.add(tid, actualdeps);
            // Double check the dependency to avoid the case that dependencies are resolved concurrently.
            actualdeps = depscheck(reads);
            if (actualdeps.isEmpty()) {
                buffer.delete(tid);
                // Grab the lock and change the state of [tid]
                
                return futurewith(true);
            } else {
                return result;
            }
        }
    }
    
    /*
     * Return a set of unresolved dependencies.
     */
    public Set<ObjectVN> depscheck(Set<ObjectVN> deps) {
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
	
	private <T> Future<T> futurewith(T value){
	    return CompletableFuture.completedFuture(value);
	}

}
