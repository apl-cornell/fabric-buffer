package util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
    
    /*
     * Grab locks for objects in [reads] and [writes] for transaction [tid].
     */
    /**
     * Grab read locks and write locks for a set of objects for a transaction.
     * Return {@code true} if all read locks and write locks are successfully
     * grabbed, and {@code false} otherwise. 
     * 
     * If the method fails to grab some locks, all locks are released.
     * 
     * @param reads A set of IDs of objects to grab read lock
     * @param writes A set of IDs of objects to grab write lock
     * @param tid ID of the transaction
     * @return A boolean in accord with whether all locks are successfully
     *         grabbed.
     */
    public boolean grabLock(Set<ObjectVN> reads, Set<ObjectVN> writes, Long tid) {
        Comparator<ObjectVN> compare = new Comparator<ObjectVN>() {
            @Override
            public int compare (ObjectVN o1, ObjectVN o2) {
                return (int)(o1.oid - o2.oid);
            }
        };
        // Sort objects to avoid deadlock
        List<ObjectVN> readlist = new ArrayList<>(reads);
        List<ObjectVN> writelist = new ArrayList<>(writes);
        readlist.sort(compare);
        writelist.sort(compare);
        
        for (ObjectVN read : readlist) {
            if (!this.lockread(tid, read.oid)) {
                releaseLock(reads, writes, tid);
                return false;
            };
        }
        
        for (ObjectVN write : writelist) {
            if (!this.lockwrite(tid, write.oid)) {
                releaseLock(reads, writes, tid);
                return false;
            }
        }
        
        return true;
    }
    
    /*
     * Release locks for objects in [reads] and [writes] for transaction [tid].
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
