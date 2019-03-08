package util;

import java.util.*;
import java.util.concurrent.Future;

import util.ObjectVN;

public interface Store {
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
    Future<Boolean> prepare(long tid,  Set<ObjectVN> reads, Set<ObjectVN> writes);
    
    /**
     * Abort a transaction.
     * 
     * @param tid The ID of the transaction.
     */
    void abort(long tid);
    
    /**
     * Commit a transaction. The version of objects in the store is updated 
     * accordingly.
     * 
     * @param tid The ID of the transaction.
     */
    void commit(long tid);
    
    /**
     * Return the current version of a object.
     * 
     * @param oid ID of the object
     * @return The version number of the object.
     */
    Long getversion(long oid);
    
    /*
     * Grab the locks for transaction [tid];
     */
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
    boolean grabLock(long tid);
}
