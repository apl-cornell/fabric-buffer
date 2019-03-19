package util;

import com.google.common.collect.Sets;

import java.util.*;
import java.util.concurrent.*;

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
    private HashMap<Store, HashSet<ObjectVN>> reads;
    
    /*
     * A map from store to objects needed to be write in that store. 
     */
    private HashMap<Store, HashSet<ObjectVN>> writes;
    
    /*
     * A pointer to the worker that owns this transaction.
     */
    private Worker worker;
    
    private ExecutorService pool;
    
    public Txn(Worker worker, long tid, HashMap<Store, HashSet<ObjectVN>> reads, HashMap<Store, HashSet<ObjectVN>> writes) {
        this.worker = worker;
        this.tid = tid;
        this.reads = reads;
        this.writes = writes;
        this.pool = Executors.newCachedThreadPool();
    }
    
    public boolean prepare() {
        // Acquire lock on the worker's side
        Set<ObjectVN> readso = new HashSet<>(Util.getSetMapValues(reads));
        Set<ObjectVN> writeso = new HashSet<>(Util.getSetMapValues(writes));
        if (!worker.grablock(readso, writeso, tid)) {
            worker.releaselock(readso, writeso, tid);
            return false;
        }
        // Submit transaction to each store
        if (TxnConcurrent) {
            List<Future<Future<Boolean>>> futures = new LinkedList<>();
            for (Store s :  Sets.union(reads.keySet(), writes.keySet())){
                Callable<Future<Boolean>> c = () -> {return s.prepare(tid, reads.get(s), writes.get(s));};
                final Future<Future<Boolean>> future = pool.submit(c);
                futures.add(future);
            }
            List<Future<Boolean>> results = new LinkedList<>();
            for (Future<Future<Boolean>> future : futures) {
                Future<Boolean> result;
                try {
                    result = future.get();
                    results.add(result);
                } catch (InterruptedException | ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            for (Future<Boolean> result : results) {
                try {
                    // if the prepare failed, abort this transaction
                    if (!result.get()) {
                        abort();
                        return false;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else {
            for (Store s : Sets.union(reads.keySet(), writes.keySet())) {
                Future<Boolean> result = s.prepare(tid, reads.get(s), writes.get(s));
                // if the prepare failed, abort this transaction
                try {
                    if (!result.get()) {
                        abort();
                        return false;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        worker.addPrepared(this);
        return true;
    }
    
    public void abort() {
        //Release lock on the worker's side
        worker.releaselock(
                new HashSet<>(Util.getSetMapValues(reads)),
                new HashSet<>(Util.getSetMapValues(writes)),
                tid
        );
    }
    
    public void commit() {
        worker.update(Util.getSetMapValues(writes));
        //Release lock on the worker's side
        worker.releaselock(
                new HashSet<>(Util.getSetMapValues(reads)),
                new HashSet<>(Util.getSetMapValues(writes)),
                tid
        );
        for (Store s : Sets.union(reads.keySet(), writes.keySet())){
            Callable<Void> c = () -> {
                s.commit(tid);
                return null;
            };
            pool.submit(c);
        }
    }
}
