package util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

public abstract class Store {
    /**
     * Prepare for a transaction. This method will return a {@code Future} that 
     * resolves with {@code true} if the transaction prepares successfully, and 
     * {@code false} if the transaction has a version conflict.
     * 
     * If there exists any version number that is unknown to the store, the 
     * transaction is added to the SmartBuffer.
     * 
     * @param tid The ID of the transaction.
     * @param reads A set of objects that the transaction reads.
     * @param writes A set of objects that the transaction writes.
     * @return A {@code Future} that resolves in accord with the transaction 
     *           prepare result.
     */
    public abstract Future<Boolean> prepare(Worker worker, long tid, Set<ObjectVN> reads, Set<ObjectVN> writes);
    
    /**
     * Abort a transaction.
     * 
     * @param tid The ID of the transaction.
     */
    public abstract void abort(long tid);
    
    /**
     * Commit a transaction. The version of objects in the store is updated 
     * accordingly.
     * 
     * @param tid The ID of the transaction.
     */
    public abstract void commit(Worker worker, long tid);
    
    /**
     * Return the current version of a object.
     * 
     * @param oid ID of the object
     * @return The version number of the object.
     */
    public abstract Long getVersion(long oid);

    /**
     * Grab the lock for objects that a transaction reads and writes on the 
     * store's side. Return {@code true} iff all the locks are successfully 
     * grabbed.
     * 
     * If the method fails to grab some lock, it releases all the locks it 
     * has grabbed.
     * 
     * @param tid ID of the transaction.
     * @return A boolean in accord with whether all locks are successfully 
     * grabbed.
     */
    public abstract boolean grabLock(long tid);

    /**
     * Set the collection of workers that are connected to the store.
     *
     * @param workers The workers.
     */
    public abstract void setWorkers(Collection<Worker> workers);

    /**
     * Get the number of pending transactions in the store.
     *
     * @return The number of pending transactions.
     */
    public abstract int pending();

    public abstract Set<Long> pendingkey();

    public abstract int numLink();

    @Override
    public String toString() {
        return String.format("Store with %d pending transactions %s and %d transactions in buffer", pending(), pendingkey(), numLink());
    }

    /*--------------------------For testing only----------------------------*/
    
    public abstract void setversion(ObjectVN object);
    
    public abstract void addpending(long tid);
}
