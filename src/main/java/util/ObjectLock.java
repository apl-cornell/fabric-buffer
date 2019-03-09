package util;

import java.util.HashSet;
import java.util.Set;

// TODO: write a javadoc for this class
public class ObjectLock {
    /*
     * [oid] of the object that this lock is associated with.
     */
    private Long oid;
    
    /*
     * [tid] of the transaction that holds the write lock.
     * [null] if no transaction holds the write lock.
     */
    private Long writelockholder;
    
    /*
     * A list of [tid] of all transactions that hold the read lock.
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
    
    /*
     * Grab the read lock of this object for transaction [tid]. 
     * Return true iff the read lock is successfully grabbed.
     */
    public synchronized boolean lockread(Long tid) {
        if (!isLocked() || isreadLocked()) {
            this.readlockholder.add(tid);
            return true;
        } else {
            return false;
        }
    }
    
    /*
     * Grab the write lock of this object for transaction [tid].
     * Return true iff the write lock is successfully grabbed.
     */
    public synchronized boolean lockwrite(Long tid) {
        if (!isLocked()) {
            this.writelockholder = tid;
            return true;
        } else {
            return false;
        }
    }
    
    /*
     * Release the read lock of this object for transaction [tid].
     */
    public synchronized void releaseread(Long tid) {
        this.readlockholder.remove(tid);
    }
    
    /*
     * Release the write lock of this object for transaction [tid].
     */
    public synchronized void releasewrite(Long tid) {
        if (this.writelockholder.equals(tid)) {
            this.writelockholder = null;
        }
    }
}
