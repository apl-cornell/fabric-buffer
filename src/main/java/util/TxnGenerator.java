package util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
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
    
    /*Distribution for transaction*/
    ProbDis probtype;
    
    /*number of reads in a transaction*/
    private int readsize;
    
    /*number of writes in a transaction*/
    private int writesize;
    
    /* Generate a new unique tid */
    private long generateTid() {
        tid++;
        return (tid - 1)*32 + wid;
    }
    
    /* Ratio of read object and write object */
    private double numObjectratio;
    private double rwratio;

    private static final int TXN_QUEUE_CAPACITY = 10;

    public TxnGenerator(Worker worker) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
    }
    
    /* Construct a txnGenerator for fixed size transactions  */
    public TxnGenerator(Worker worker, int readsize, int writesize) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
        this.probtype = ProbDis.FixedSize;
        this.readsize = readsize;
        this.writesize = writesize;
    }
    
    /**/
    public TxnGenerator(Worker worker, ProbDis probtype, double numObjectratio, double rwratio) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
        this.probtype = probtype;
        this.numObjectratio = numObjectratio;
        this.rwratio = rwratio;
    }
    
    /* Generate a new transaction */
    private void newTxn() {
        HashMap<Store, HashSet<ObjectVN>> reads = new HashMap<>();
        HashMap<Store, HashSet<ObjectVN>> writes = new HashMap<>();
        
        // Randomly generate reads
        switch(probtype) {
            case FixedSize:
                // TODO : Any better way to do this?
                // Generate sorted distinct random index
                int size = worker.lastversion.keySet().size();
                ArrayList<Integer> list = new ArrayList<Integer>();
                for (int i = 0; i < size; i ++) {
                    list.add(new Integer(i));
                }
                Collections.shuffle(list);
                LinkedList<Integer> rwlist = new LinkedList<Integer>();
                for (int i = 0; i < readsize + writesize; i++) {
                    rwlist.add(list.get(i));
                }
                Collections.sort(rwlist);
                
                //Pick items from worker.lastversion based on rwlist
                int i = 0;
                int num = 0;
                for (long oid : worker.lastversion.keySet()) {
                    if (i == rwlist.peek()) {
                        if (num < readsize) {
                            Util.addToSetMap(reads, worker.location.get(oid), 
                                    new ObjectVN(oid, worker.lastversion.get(oid)));
                        } else {
                            Util.addToSetMap(writes, worker.location.get(oid), 
                                    new ObjectVN(oid, worker.lastversion.get(oid) + 1));
                        }
                        num++;
                        rwlist.removeFirst();
                    }
                    
                }
                break;
                
                
            case Uniform: 
                for (long oid : worker.lastversion.keySet()) {
                    Random rand = new Random();
                    double random = rand.nextDouble();
                    if (random < numObjectratio) {
                        random = Math.random();
                        if (random < rwratio) {
                            Util.addToSetMap(writes, worker.location.get(oid), 
                                    new ObjectVN(oid, worker.lastversion.get(oid) + 1));
                        }
                        Util.addToSetMap(reads, worker.location.get(oid), 
                                new ObjectVN(oid, worker.lastversion.get(oid)));
                    }
                }
                break;
            
            case Gaussian:
                for (long oid : worker.lastversion.keySet()) {
                    Random rand = new Random();
                    double random = rand.nextGaussian() + 0.5;
                    if (random < numObjectratio) {
                        random = Math.random();
                        if (random < rwratio) {
                            Util.addToSetMap(writes, worker.location.get(oid), 
                                    new ObjectVN(oid, worker.lastversion.get(oid) + 1));
                        }
                        Util.addToSetMap(reads, worker.location.get(oid), 
                                new ObjectVN(oid, worker.lastversion.get(oid)));
                    }
                }
                break;
        }
        
        long ntid = generateTid();
        
        Txn new_txn = new Txn(worker, ntid, reads, writes);
        try {
            queue.put(new_txn);
        } catch (InterruptedException e) {
            // TODO: should we handle this somehow?
        }
    }
    
    /*type of probability distribution*/
    public enum ProbDis {
        FixedSize, Uniform, Gaussian
    }
}
