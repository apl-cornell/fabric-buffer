package util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;

import com.google.common.collect.SetMultimap;

import smartbuffer.SmartBuffer;
import util.ObjectLockTable;

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
    
    /*
     * A locktable for object-level lock.
     */
    public ObjectLockTable locktable;
    
    public StoreSB(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new SetMultimap<>();
        this.pendingread = new SetMultimap<>();
        this.locktable = new ObjectLockTable();
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
            // Grab the lock on the store's side
            return futurewith(locktable.grabLock(reads,writes,tid));
        } else {
            // Result resolved to true if the dependencies of [tid] are resolved. resolved to false only when there is version conflict
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            buffer.add(tid, reads, result);
            return result;
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
    	for (ObjectVN write : pending.get(tid)) {
    	    lastversion.put(write.oid, write.vnum);
    	    //Release Lock
    	    locktable.releaseLock(pendingread.get(tid), pending.get(tid), tid);
    	    //Remove object from the buffer
    	    buffer.remove(write);
            //Prepare objects that have all dependencies removed
    	    for (long tid1 : translist) {
    	        //Check version conflict
    	        boolean novc = true;
    	        for (ObjectVN read : pendingread.get(tid1)) {
    	            if (novc && read.vnum < lastversion.get(read.oid)) {
    	                futures.get(tid1).complete(false);
    	                novc = false;
    	                break;
    	            }
    	        }
    	        if (novc) {
    	         // Grab the lock on the store's side
    	            Set<ObjectVN> reads = pendingread.get(tid1);
    	            Set<ObjectVN> writes = pending.get(tid1);
    	            futures.get(tid1).complete(locktable.grabLock(reads, writes, tid1));
    	        }
    	        futures.remove(tid1);
    	    }
    	    //Abort transactions with version conflict to this commit
    	    List<Long> ejectlist = buffer.eject(write);
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

    @Override
    public Long getversion(long oid) {
        return lastversion.get(oid);
    }

    @Override
    public boolean grabLock(long tid) {
        return locktable.grabLock(pendingread.get(tid), pending.get(tid), tid);
    }
}
