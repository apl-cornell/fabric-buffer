package util;

import java.util.*;
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
    
    /*The biggest unused oid*/
    private long oid;
    
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
    
    /* Generate a new unique oid */
    private long generateOid() {
        oid++;
        return (oid - 1)*32 + oid;
    }
    
    /* Ratio of read object and write object */
    private double numObjectratio;
    private double rwratio;

    private static final int TXN_QUEUE_CAPACITY = 10;
    
    private Txn initialtxn(int initial_cap) {
        HashMap<Store, HashSet<ObjectVN>> reads = new HashMap<>();
        HashMap<Store, HashSet<ObjectVN>> writes = new HashMap<>();
        
        for (Store s : worker.storelist) {
            HashSet<ObjectVN> w = new HashSet<>();
            for (int i = 0; i < initial_cap; i++) {
                long noid = generateOid();
                ObjectVN object = new ObjectVN(noid, 0);
                w.add(object);
                worker.location.put(noid, s);
            }
            writes.put(s, w);
        }
        
        long ntid = generateTid();
        Txn initial_txn = new Txn(worker, ntid, reads, writes);
        return initial_txn;
    }

    public TxnGenerator(Worker worker) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
        worker.setqueue(queue);
    }
    
    /* Construct a txnGenerator for fixed size transactions  */
    public TxnGenerator(Worker worker, int readsize, int writesize, int initial_cap) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
        this.probtype = ProbDis.FixedSize;
        this.readsize = readsize;
        this.writesize = writesize;
        this.tid = 0;
        this.oid = 0;
        worker.setqueue(queue);
        try {
            queue.put(initialtxn(initial_cap));
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /**/
    public TxnGenerator(Worker worker, ProbDis probtype, double numObjectratio, double rwratio, int initial_cap) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
        this.probtype = probtype;
        this.numObjectratio = numObjectratio;
        this.rwratio = rwratio;
        this.tid = 0;
        this.oid = 0;
        worker.setqueue(queue);
        try {
            queue.put(initialtxn(initial_cap));
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /* Generate a new transaction */
    public void newTxn() {
        HashMap<Store, HashSet<ObjectVN>> reads = new HashMap<>();
        HashMap<Store, HashSet<ObjectVN>> writes = new HashMap<>();
        
        // Randomly generate reads
        switch(probtype) {
            case FixedSize:
                // make sure the total number of transactions is readsize + writesize
                Set<Long> objects = worker.lastversion.keySet();
                HashSet<Long> rwSet = randomSubset(objects, readsize + writesize);

                int writesSoFar = 0;
                for (long oid : worker.lastversion.keySet()) {
                    // this is O(1)
                    if (rwSet.contains(oid)) {
                        // this is a read or a write
                        // at the very least, this is a read
                        Store location = worker.location.get(oid);
                        ObjectVN readObject = new ObjectVN(oid, worker.lastversion.get(oid));
                        Util.addToSetMap(reads,location, readObject);

                        // upgrade the first [writesize] transactions to writes
                        if (writesSoFar < writesize) {
                            // upgrade to a write
                            ObjectVN writeObject = new ObjectVN(oid, worker.lastversion.get(oid) + 1);
                            Util.addToSetMap(writes, location, writeObject);
                            writesSoFar++;
                        }
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

    private <T> HashSet<T> randomSubset(Set<T> set, int size) {
        List<T> list = new LinkedList<>(set);
        Collections.shuffle(list);
        return new HashSet<>(list.subList(0, size));
    }
}
