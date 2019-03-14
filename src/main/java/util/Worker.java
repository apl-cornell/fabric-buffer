package util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import util.ObjectLockTable;

public class Worker {
    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    public HashMap<Long, Long> lastversion;
    
    /*
     * A map from [oid] to RWlock.
     */
    private ObjectLockTable locktable;
    
    /*
     * The set of transactions prepared successfully
     */
    private Set<Txn> prepared;
    
    /*
     * Location of each object
     */
    public HashMap<Long, Store> location;
    
    /*
     * Worker ID.
     */
    private int wid;
    
    /*
     * Update the version number of object.
     */
    public void update(Collection<ObjectVN> collection) {
        for (ObjectVN write : collection) {
            lastversion.put(write.oid, write.vnum);
        }
    }
    
    /*
     * Add a transaction to the set of prepared transactions.
     */
    public void addPrepared(Txn t) {
        prepared.add(t);
    }
    
    /*
     * grab locks.
     */
    public boolean grablock(Set<ObjectVN> reads, Set<ObjectVN> writes, Long tid) {
        return locktable.grabLock(reads, writes, tid);
    }
    
    /*
     * release locks.
     */
    public void releaselock(Set<ObjectVN> reads, Set<ObjectVN> writes, Long tid) {
        locktable.releaseLock(reads, writes, tid);
    }
}
