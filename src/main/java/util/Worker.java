package util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class Worker {
    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    public HashMap<Long, Long> lastversion;
    
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
    
    private ExecutorService pool;
    
    
    public Worker(int wid, List<Store> storelist, boolean concur) {
        lastversion = new HashMap<>();
        locktable = new ObjectLockTable();
        prepared = (new ConcurrentHashMap<Txn,Integer>()).keySet();
        location = new HashMap<>();
        this.wid = wid;
        this.storelist = storelist;
        WORKER_CONCUR = concur;
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
     * Add a transaction to the set of prepared transactions.
     */
    public void addPrepared(Txn t) {
        if (prepared.isEmpty()) {
            prepared.add(t);
            prepared.notify();
        } else {
            prepared.add(t);
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
     * Take a transaction from the generator and prepare the transaction. 
     */
    public void startnewtxn() {
        if (WORKER_CONCUR) {
            Thread t = new Thread(new TxnPrepareThread());
            t.start();
        } else {
            try {
                Txn newtxn = queue.take();
                boolean res = newtxn.prepare();
                if (res) {
                    newtxn.commit();
                } else {
                    // TODO : Handle version conflict
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    /*
     * Take a transaction from prepared and prepare the transaction.
     */
    public void committxn() {
        Runnable task = () -> {
            try {
                while (prepared.isEmpty()) {
                    prepared.wait();
                }
                for (Txn newtxn : prepared) {
                    newtxn.commit();
                    prepared.remove(newtxn);
                    break;
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        };
        
        if (WORKER_CONCUR) {
            Thread t = new Thread(task);
            t.start();
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
}
