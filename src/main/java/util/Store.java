package util;

import java.util.*;
import java.util.concurrent.Future;

import util.ObjectVN;

public interface Store {
    /*
     * Prepare for transaction [tid] that reads [reads] and writes [writes].
     */
    public Future<Boolean> prepare(long tid,  Set<ObjectVN> reads, Set<ObjectVN> writes);
    
    /*
     * Abort transaction [tid].
     */
    public void abort(long tid);
    
    /*
     * Commit transaction [tid].
     */
    public void commit(long tid);
}