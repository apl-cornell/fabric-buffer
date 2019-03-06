package util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

public class Worker {
    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    private HashMap<Long, Long> lastversion;
    
    /*
     * A map from [oid] to RWlock.
     */
    private HashMap<Long, ReadWriteLock> locktable;
    
    /*
     * The set of transactions prepared successfully
     */
    private Set<Txn> prepared;
    
    /*
     * Location of each object
     */
    private HashMap<Long, Store> location;
    
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
    public boolean grablock(Collection<ObjectVN> collection, Collection<ObjectVN> collection2) {
        for (ObjectVN read : collection) {
            if (!locktable.get(read.oid).readLock().tryLock()) {
                return false;
            }
        }
        for (ObjectVN write : collection2) {
            if (!locktable.get(write.oid).writeLock().tryLock()) {
                return false;
            }
        }
        return true;
    }
    
    /*
     * release locks.
     */
    public void releaselock(Collection<ObjectVN> collection, Collection<ObjectVN> collection2) {
        for (ObjectVN read : collection) {
            locktable.get(read.oid).readLock().unlock();
        }
        for (ObjectVN write : collection2) {
            locktable.get(write.oid).writeLock().unlock();
        }
    }
}
