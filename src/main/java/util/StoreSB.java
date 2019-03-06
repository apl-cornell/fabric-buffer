package util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.google.common.collect.SetMultimap;

import smartbuffer.SmartBuffer;

public class StoreSB implements Store {
    public SmartBuffer buffer;
    
    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    public HashMap<Long, Long> lastversion;
    
    /*
     * A map from [tid] to objects that transaction writes for transactions waiting for processing.
     */
    public SetMultimap<Long, ObjectVN> pending;
    
    /*
     * A map from [tid] to objects that transaction reads for transactions waiting for processing.
     */
    public SetMultimap<Long, ObjectVN> pendingread;
    
    /*
     * A map from transactions in the buffer to associated futures to be filled.
     */
    public HashMap<Long, CompletableFuture<Boolean>> futures;
    
    public StoreSB(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new SetMultimap<>();
        this.pendingread = new SetMultimap<>();
    }
    
    @Override
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
            buffer.add(tid, actualdeps);
            // Double check the dependency to avoid the case that dependencies are resolved concurrently.
            actualdeps = depscheck(reads);
            if (actualdeps.isEmpty()) {
                buffer.delete(tid);
                // Grab the lock and change the state of [tid]
                
                return futurewith(true);
            } else {
                // Add a result to be filled to the futures. 
                // Resolve [result] when the transaction is prepared successfully or ejected because of version conflict
                CompletableFuture<Boolean> result = new CompletableFuture<>();
                futures.put(tid, result);
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
    
    @Override
    public void commit(long tid) {
    	for (ObjectVN object : pending.get(tid)) {
    	    lastversion.put(object.oid, object.vnum);
    	    //Remove object from the buffer
    	    List<Long> translist = buffer.remove(object);
            //Prepare objects that have all dependencies removed
    	    for (long tid1 : translist) {
    	        //Check version conflict
    	        boolean novc = true;
    	        for (ObjectVN read : pendingread.get(tid1)) {
    	            if (novc && read.vnum < lastversion.get(read.oid)) {
    	                futures.get(tid1).complete(false);
    	                novc = false;
    	            }
    	        }
    	        if (novc) {
    	            futures.get(tid1).complete(true);
    	        }
    	        futures.remove(tid1);
    	    }
    	    //Abort transactions with version conflict to this commit
    	    List<Long> ejectlist = buffer.eject(object);
    	    for (long tid1 : ejectlist) {
    	        abort(tid1);
    	    }
    	    
    	}
    	pending.removeAll(tid);
    }

	@Override
	public void abort(long tid) {
		pending.removeAll(tid);
		if (futures.containsKey(tid)) {
		    futures.get(tid).complete(false);
		    futures.remove(tid);
		}
		
	}
	
	private <T> Future<T> futurewith(T value){
	    return CompletableFuture.completedFuture(value);
	}

}
