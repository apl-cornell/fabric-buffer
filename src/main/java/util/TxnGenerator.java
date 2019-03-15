package util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
        HashMap<Store, HashSet<ObjectVN>> reads = new HashMap<>();
        HashMap<Store, HashSet<ObjectVN>> writes = new HashMap<>();
        
        // Randomly generate reads
        for (long oid : worker.lastversion.keySet()) {
            double random = Math.random();
            if (random > readsratio) {
                Util.addToSetMap(reads, worker.location.get(oid),
                        new ObjectVN(oid, worker.lastversion.get(oid)));
            }
        }
        
        // Pick writes from reads
        for (ObjectVN object : Util.getSetMapValues(reads)) {
            double random = Math.random();
            if (random > writesratio) {
                Util.addToSetMap(writes, worker.location.get(object.oid),
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
