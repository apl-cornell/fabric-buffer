package util;

import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.util.*;
import java.util.concurrent.*;

import util.ObjectVN;
import util.Store;
import util.Worker;

public class Txn {
    /*
     * If true, submit transactions to multiple stores concurrently.
     */
    public static boolean TxnConcurrent;
    
    /*
     * Transaction id.
     */
    private long tid;
    
    /*
     * A map from store to objects needed to be read in that store.
     */
    private SetMultimap<Store, ObjectVN> reads;
    
    /*
     * A map from store to objects needed to be write in that store. 
     */
    private SetMultimap<Store, ObjectVN> writes;
    
    /*
     * A pointer to the worker that owns this transaction.
     */
    private Worker worker;
    
    private ExecutorService pool;
    
    public Txn(Worker worker, long tid, SetMultimap<Store, ObjectVN> reads, SetMultimap<Store, ObjectVN> writes) {
        this.worker = worker;
        this.tid = tid;
        this.reads = reads;
        this.writes = writes;
        this.pool = Executors.newCachedThreadPool();
    }
    
    public void prepare() {
        // Acquire lock on the worker's side
        
        // Submit transaction to each store
        if (TxnConcurrent) {
            List<Future<Boolean>> results = new LinkedList<>();
            for (Store s :  Sets.union(reads.keySet(), writes.keySet())){
                Callable<Boolean> c = () -> {return s.prepare(tid, reads.get(s), writes.get(s));};
                final Future<Boolean> result = pool.submit(c);
                results.add(result);
            }
            for (Future<Boolean> result : results) {
                try {
                    // if the prepare failed, abort this transaction
                    // TODO look for method to handle list of futures like Promise.all in javascript
                    if (!result.get()) {
                        abort();
                        break;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else {
            for (Store s : Sets.union(reads.keySet(), writes.keySet())) {
                boolean result = s.prepare(tid, reads.get(s), writes.get(s));
                // if the prepare failed, abort this transaction
                if (!result) {
                    abort();
                    break;
                }
            }
        }
    }
    
    public void abort() {
        
    }
}
