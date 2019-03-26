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

    /* Generator for transaction sizes */
    private RandomGenerator gen;
    
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
        this(worker, RandomGenerator.constant(0.5f), 5);
    }
    
//    /* Construct a txnGenerator for fixed size transactions  */
//    public TxnGenerator(Worker worker, int readsize, int writesize, int initial_cap) {
//        this.worker = worker;
//        this.wid = worker.wid;
//        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
//        this.readsize = readsize;
//        this.writesize = writesize;
//        this.tid = 0;
//        this.oid = 0;
//        worker.setqueue(queue);
//        try {
//            queue.put(initialtxn(initial_cap));
//        } catch (InterruptedException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
//
//    /**/
//    public TxnGenerator(Worker worker, RandomGenerator gen, double numObjectratio, double rwratio, int initial_cap) {
//        this.worker = worker;
//        this.wid = worker.wid;
//        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
//        this.gen = gen;
//        this.numObjectratio = numObjectratio;
//        this.rwratio = rwratio;
//        this.tid = 0;
//        this.oid = 0;
//        worker.setqueue(queue);
//        try {
//            queue.put(initialtxn(initial_cap));
//        } catch (InterruptedException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }

    public TxnGenerator(Worker worker, RandomGenerator gen, int initialCap) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(TXN_QUEUE_CAPACITY);
        this.gen = gen;
        this.tid = 0;
        this.oid = 0;
        worker.setqueue(queue);
        try {
            queue.put(initialtxn(initialCap));
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /* Generate a new transaction */
    public void newTxn() {
        HashMap<Store, HashSet<ObjectVN>> reads = new HashMap<>();
        HashMap<Store, HashSet<ObjectVN>> writes = new HashMap<>();

        // generate readsize and writesize
        Set<Long> objects = worker.lastversion.keySet();
        int interactions = (int) Math.floor(this.gen.random() * objects.size());
        int writesize = (int) Math.floor(interactions * this.gen.random());
        int readsize = interactions - writesize;

        // make sure the total number of transactions is readsize + writesize
        List<Long> rwSet = randomSubset(objects, readsize + writesize);

        int writesSoFar = 0;
        for (long oid : rwSet) {
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
        
        long ntid = generateTid();
        
        Txn new_txn = new Txn(worker, ntid, reads, writes);
        try {
            queue.put(new_txn);
        } catch (InterruptedException e) {
            // TODO: should we handle this somehow?
        }
    }

    /**
     * Get a random subset of a set with a certain size. The subset is in
     * random order.
     *
     * @param set The set.
     * @param size The size of the random subset. Cannot be greater than the
     *             size of {@code set} or less than zero.
     * @param <T> The type of the set's elements.
     * @return A random subset of size {@code size}, in random order.
     */
    private <T> List<T> randomSubset(Set<T> set, int size) {
        List<T> list = new LinkedList<>(set);
        Collections.shuffle(list);
        return list.subList(0, size);
    }
}
