package util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

public class TxnGenerator {
    /* Worker this generator is associated with */
    private Worker worker;
    
    /* Worker ID*/
    private long wid;
    
    /*queue to communicate with worker */
    private BlockingQueue<Txn> queue;
    
    /*The biggest unused tid */
    private long tid;
    
    /* Generate a new unique tid */
    private long generateTid() {
        tid++;
        return (tid - 1)*32 + wid;
    }
    
    /* Ratio of read object and write object */
    private static double readsratio;
    private static double writesratio;

    private static final int TXN_QUEUE_CAPACITY = 10;

    public TxnGenerator(Worker worker) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
    }
    
    /* Generate a new transaction */
    private void newTxn() {
        SetMultimap<Store, ObjectVN> reads = new HashMultimap<>();
        SetMultimap<Store, ObjectVN> writes = new HashMultimap<>();
        
        // Randomly generate reads
        for (long oid : worker.lastversion.keySet()) {
            double random = Math.random();
            if (random > readsratio) {
                reads.put(worker.location.get(oid), 
                        new ObjectVN(oid, worker.lastversion.get(oid)));
            }
        }
        
        // Pick writes from reads
        for (ObjectVN object : reads.values()) {
            double random = Math.random();
            if (random > writesratio) {
                writes.put(worker.location.get(object.oid), 
                        new ObjectVN(object.oid, object.vnum + 1));
            }
        }
        
        long ntid = generateTid();
        
        Txn new_txn = new Txn(worker, ntid, reads, writes);
        try {
            queue.put(new_txn);
        } catch (InterruptedException e) {
            // TODO: should we handle this somehow?
        }
    }
}
