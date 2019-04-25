package util;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

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
    private AtomicLong last_unused_oid;

    /* Generator for transaction sizes */
    private RandomGenerator gen;

    public int txn_created;

    /* Ratio of reads to writes */
    private float writeRatio;

    /* Generate a new unique tid */
    private long generateTid() {
        tid++;
        return (tid - 1)*32 + wid;
    }
    
    /* Generate a new unique oid */
    private long generateOid() {
        long oid = last_unused_oid.incrementAndGet();
        return oid;
    }

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
        this(worker, RandomGenerator.constant(0.001f), 0.001f, 10, new AtomicLong());
    }

    public TxnGenerator(Worker worker, RandomGenerator gen, float writeRatio, int txn_queue_capacity, AtomicLong last_unused_oid) {
        this.worker = worker;
        this.wid = worker.wid;
        this.queue = new ArrayBlockingQueue<>(txn_queue_capacity);
        this.gen = gen;
        this.tid = 0;
        this.last_unused_oid = last_unused_oid;
        this.writeRatio = writeRatio;
        worker.setqueue(queue);
        this.txn_created = 1;
    }

    /* Generate a new transaction */
    public void newTxn() {
        // generate readsize and writesize
        Set<Long> objects = worker.lastversion.keySet();
        int interactions = (int) Math.floor(this.gen.random() * objects.size());
        int writesize = (int) (interactions * this.writeRatio);
        //(int) Math.floor(interactions * this.gen.random());
        int readsize = interactions - writesize;

        txn(readsize, writesize);
    }

    public void newTestTxn() {
        int writesize = 0;
        int readsize = 2;

        txn(readsize, writesize);
    }

    private void txn(int readsize, int writesize){
        HashMap<Store, HashSet<ObjectVN>> reads = new HashMap<>();
        HashMap<Store, HashSet<ObjectVN>> writes = new HashMap<>();

        Set<Long> objects = worker.lastversion.keySet();

        Long[] rwSet = randomSet(readsize + writesize);

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
            txn_created++;
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
    private static <T> List<T> randomSubset(Set<T> set, int size) {
        List<T> list = new LinkedList<>(set); // TODO: this is still inefficient!
        return randomSubset(list, size);
    }

    // https://stackoverflow.com/a/35278327/1175276
    private static <E> List<E> randomSubset(List<E> list, int n, Random r) {
        int length = list.size();

        if (length < n) return null;

        // We don't need to shuffle the whole list
        for (int i = length - 1; i >= length - n; --i) {
            Collections.swap(list, i , r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }

    private static <E> List<E> randomSubset(List<E> list, int n) {
        return randomSubset(list, n, ThreadLocalRandom.current());
    }


    // Fast approximation of reservoir sampling
    private Long[] randomSet(int size) {
        Long[] res = new Long[size];

        for (int i = 0; i < size; i++) {
            res[i] = (long)i;
        }

        long t = 4 * size;
        long i = size;
        while (i < this.last_unused_oid.get() && i < t) {
            int j = (int)(Math.random()*i);
            if (j < size){
                res[j] = i;
            }
            i = i + 1;
        }

        while (i < this.last_unused_oid.get()) {
            double p = (1.0*size)/i;
            double u = Math.random();
            int g = (int)(Math.log(u)/Math.log(1 - p));
            i = i + g;
            if (i < this.last_unused_oid.get()) {
                int j = (int)(Math.random()*size);
                res[j] = i;
                i = i + 1;
            }
        }

        return res;
    }
}
