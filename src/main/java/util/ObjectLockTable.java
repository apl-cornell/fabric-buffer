package util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import util.ObjectLock;

public class ObjectLockTable {
    /*
     * A map from [oid] to the associated ObjectLock
     */
    private ConcurrentHashMap<Long, ObjectLock> locktable;
    
    /**
     * Create a new ObjectLockTable
     */
    public ObjectLockTable() {
        this.locktable = new ConcurrentHashMap<>();
    }
    
    /*
     * Return the ObjectLock associated with object [oid].
     * A lock is created if such lock does not exist.
     */
    private ObjectLock getLock(Long oid) {
        ObjectLock lock = new ObjectLock(oid);
        ObjectLock existing = locktable.putIfAbsent(oid, lock);
        return existing == null? lock : existing;
    }
    
    /*
     * Grab a write lock of [oid] for transaction [tid].
     */
    private boolean lockwrite(Long tid, Long oid) {
        ObjectLock lock = this.getLock(oid);
        return lock.lockwrite(tid);
    }
    
    /*
     * Release the write lock of [oid] for transaction [tid].
     */
    private void releasewrite(Long tid, Long oid) {
        ObjectLock lock = this.getLock(oid);
        lock.releasewrite(tid);
    }
    
    /*
     * Grab a read lock of [oid] for transaction [tid].
     */
    private boolean lockread(Long tid, Long oid) {
        ObjectLock lock = this.getLock(oid);
        return lock.lockread(tid);
    }
    
    /*
     * Release the read lock of [oid] for transaction [tid].
     */
    private void releaseread(Long tid, Long oid) {
        ObjectLock lock = this.getLock(oid);
        lock.releaseread(tid);
    }

    /**
     * Grab read locks and write locks for a set of objects for a transaction.
     * Return {@code true} if all read locks and write locks are successfully
     * grabbed, and {@code false} otherwise. 
     * 
     * If the method fails to grab some locks, all locks are released.
     * 
     * @param reads A set of IDs of objects the read locks of which needs to be
     *              grabbed
     * @param writes A set of IDs of objects the write locks of which needs to 
     *               be grabbed
     * @param tid ID of the transaction
     * @return A boolean in accord with whether all locks are successfully
     *         grabbed
     */
    public boolean grabLock(Set<ObjectVN> reads, Set<ObjectVN> writes, Long tid) {
        // Generate oid set
        Set<Long> idset = new HashSet<>();
        Set<Long> writeid = new HashSet<>();

        for (ObjectVN obj : reads) {
            idset.add(obj.oid);
        }

        for (ObjectVN obj : writes) {
            idset.add(obj.oid);
            writeid.add(obj.oid);

        }

        // Sort oids to avoid deadlock
        List<Long> list = new ArrayList<>(idset);
        Collections.sort(list);
        
        for (Long oid : list) {
            if (writeid.contains(oid)){
                if (!this.lockwrite(tid, oid)) {
                    releaseLock(reads, writes, tid);
                    return false;
                }
            } else {
                if (!this.lockread(tid, oid)) {
                    releaseLock(reads, writes, tid);
                    return false;
                }
            }

        }
        
        return true;
    }
    
    /*
     * Release locks for objects in [reads] and [writes] for transaction [tid].
     */
    /**
     * Grab read locks and write locks for a set of objects for a transaction.
     * 
     * @param reads A set of IDs of objects the read locks of which needs to be 
     *              released
     * @param writes A set of IDs of objects the write locks of which needs to 
     *               be released. 
     * @param tid tid ID of the transaction
     */
    public void releaseLock(Set<ObjectVN> reads, Set<ObjectVN> writes, Long tid) {
        for (ObjectVN read : reads) {
            releaseread(tid, read.oid);
        }
        for (ObjectVN write : writes) {
            releasewrite(tid, write.oid);
        }
    }
}
