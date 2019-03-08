package util;

import java.util.*;
import java.util.concurrent.Future;

import util.ObjectVN;

public interface Store {
    /*
     * Prepare for transaction [tid] that reads [reads] and writes [writes].
     */
    Future<Boolean> prepare(long tid,  Set<ObjectVN> reads, Set<ObjectVN> writes);
    
    /*
     * Abort transaction [tid].
     */
    void abort(long tid);
    
    /*
     * Commit transaction [tid].
     */
    void commit(long tid);
    
    /*
     * Return the current version of [oid].
     */
    Long getversion(long oid);
    
    /*
     * Grab the locks for transaction [tid];
     */
    boolean grabLock(long tid);
}
