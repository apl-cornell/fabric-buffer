package util;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newFixedThreadPool;

public class Worker {
    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    public ConcurrentHashMap<Long, Long> lastversion;

    /*
     * A map from [oid] to RWlock.
     */
    private ObjectLockTable locktable;

    /*
     * The set of transactions prepared successfully
     */
    private Set<Txn> prepared;

    /*
     * Location of each object
     */
    public HashMap<Long, Store> location;

    /*
     * Worker ID.
     */
    public int wid;

    /*
     * queue to communicate with txn Generator
     */
    private BlockingQueue<Txn> queue;

    /*
     * List of existing stores.
     */
    public List<Store> storelist;

    /*
     * Whether worker prepare and commit transactions concurrently.
     */
    private static boolean WORKER_CONCUR;

    /*
     * The number of transactions committed by this worker.
     */
    private int num_commits;

    private int num_aborts;

    /*
     * The number of txns aborted because cannot grab lock on the workers's side.
     */
    public int num_abort_lock;

    public Store homestore;

    public int home_inv;

    public int non_home_inv;


    public ExecutorService pool;


    public Worker(int wid, List<Store> storelist, boolean concur) {
        lastversion = new ConcurrentHashMap<>();
        locktable = new ObjectLockTable();
        prepared = ConcurrentHashMap.newKeySet();
        location = new HashMap<>();
        this.wid = wid;
        this.storelist = storelist;
        WORKER_CONCUR = concur;
        num_commits = 0;
        num_aborts = 0;
        num_abort_lock = 0;
        if (concur) {
            pool = newFixedThreadPool(8);
            // pool = newCachedThreadPool();
        }
    }

    public Worker(int wid, List<Store> storelist, boolean concur, HashMap<Long, Long> lastversion, HashMap<Long, Store> location, int poolsize, Store homestore, int home_inv, int non_home_inv) {
        locktable = new ObjectLockTable();
        prepared = ConcurrentHashMap.newKeySet();
        this.wid = wid;
        this.storelist = storelist;
        WORKER_CONCUR = concur;
        num_commits = 0;
        num_aborts = 0;
        num_abort_lock = 0;
        if (concur) {
            pool = newFixedThreadPool(poolsize);
            // pool = newCachedThreadPool();
        }

        this.homestore = homestore;
        this.home_inv = home_inv;
        this.non_home_inv = non_home_inv;

        this.lastversion = new ConcurrentHashMap<>(lastversion);
        this.location = location;
    }

    /*
     * Set the queue.
     */
    public void setqueue(BlockingQueue<Txn> queue) {
        this.queue = queue;
    }

    /*
     * Update the version number of object.
     */
    public void update(Collection<ObjectVN> collection) {
        for (ObjectVN write : collection) {
            lastversion.put(write.oid, write.vnum);
        }
    }


    /*
     * grab locks.
     */
    public boolean grablock(Set<ObjectVN> reads, Set<ObjectVN> writes, Long tid) {
        return locktable.grabLock(reads, writes, tid);
    }

    /*
     * release locks.
     */
    public void releaselock(Set<ObjectVN> reads, Set<ObjectVN> writes, Long tid) {
        locktable.releaseLock(reads, writes, tid);
    }

    /*
     * add new object
     */
    public void addObject(Store s, ObjectVN object) {
        if (!lastversion.containsKey(object.oid)) {
            lastversion.put(object.oid, object.vnum);
            location.put(object.oid, s);
        }
    }

    /*
     * Take a transaction from the generator and prepare the transaction.
     */
    public void startnewtxn() {
//        if (WORKER_CONCUR) {
//            Thread t = new Thread(new TxnPrepareThread());
//            pool.submit(t);
//            t.start();
//        } else {
            try {
                Txn newtxn = queue.take();
                boolean res = newtxn.prepare();
                if (res) {
                    newtxn.commit();
                    num_commits++;
                } else {
                    num_aborts++;
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
//        }
    }

    /*
     * Take a transaction from prepared and prepare the transaction.
     */
    public void committxn() {
        Runnable task = () -> {
            try {
                synchronized (prepared){
                    while (prepared.isEmpty()) {
                        prepared.wait();
                    }
                    for (Txn newtxn : prepared) {
                        newtxn.commit();
                        prepared.remove(newtxn);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        };

        if (WORKER_CONCUR) {
            Thread t = new Thread(task);
            pool.submit(t);
//            t.start();
        } else {
            task.run();
        }
    }

    class TxnPrepareThread implements Runnable {

        @Override
        public void run() {
            try {
                Txn newtxn = queue.take();
                boolean res = newtxn.prepare();
                if (!res) {
                    // TODO : Handle version conflict
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

    @Override
    public String toString(){
        return String.format("This worker completed %d transactions, aborted %d transactions in total, %d of which were due to a lock conflict",
                num_commits, num_aborts, num_abort_lock);
    }
}
