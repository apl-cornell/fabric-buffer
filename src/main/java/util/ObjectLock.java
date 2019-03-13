package util;

import java.util.HashSet;
import java.util.Set;

/**
 * ReadWriteLock that locks objects for transactions.
 */
public class ObjectLock {
    /**
     * ID of the object that this lock is associated with.
     */
    private Long oid;
    
    /**
     * ID of the transaction that holds the write lock.
     * [null] if no transaction holds the write lock.
     */
    private Long writelockholder;
    
    /**
     * A list of IDs of transactions that hold the read lock.
     * The list is empty if no transaction holds the read lock.
     */
    private Set<Long> readlockholder;
    
    public ObjectLock(Long oid) {
        this.oid = oid;
        this.writelockholder = null;
        this.readlockholder = new HashSet<>();
    }
    
    /*
     * Return true iff the object is locked.
     */
    private synchronized boolean isLocked() {
        return (writelockholder != null || !readlockholder.isEmpty());
    }
    
    /*
     * Return true iff the object's read lock is held by some transaction.
     */
    private synchronized boolean isreadLocked() {
        return (!readlockholder.isEmpty());
    }

    /**
     * Grab the read lock of this object for a transaction. Return {@code true}
     * if the read lock is successfully grabbed, and {@code false} otherwise.
     * 
     * @param tid ID of the transaction
     * @return A boolean in accord with whether the read lock is successfully 
     *         grabbed.
     */
    public synchronized boolean lockread(Long tid) {
        if (!isLocked() || isreadLocked()) {
            this.readlockholder.add(tid);
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Grab the write lock of this object for a transaction. Return {@code true}
     * if the write lock is successfully grabbed, and {@code false} otherwise.
     * 
     * @param tid ID of the transaction
     * @return A boolean in accord with whether the write lock is successfully 
     *         grabbed.
     */
    public synchronized boolean lockwrite(Long tid) {
        if (!isLocked()) {
            this.writelockholder = tid;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Release the read lock of this object for a transaction.
     * 
     * @param tid ID of the transaction
     */
    public synchronized void releaseread(Long tid) {
        this.readlockholder.remove(tid);
    }
    
    /**
     * Release the write lock of this object for a transaction.
     * 
     * @param tid ID of the transaction
     */
    public synchronized void releasewrite(Long tid) {
        if (this.writelockholder.equals(tid)) {
            this.writelockholder = null;
        }
    }
}
