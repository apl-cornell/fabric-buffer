package util;

import smartbuffer.SmartBuffer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

public class StoreSB extends Store {
    private SmartBuffer buffer;

    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    private HashMap<Long, Long> lastversion;

    /*
     * A map from [tid] to objects that transaction writes for transactions waiting for processing.
     */
    private ConcurrentHashMap<Long, HashSet<ObjectVN>> pending;

    /*
     * A map from [tid] to objects that transaction reads for transactions waiting for processing.
     */
    private ConcurrentHashMap<Long, HashSet<ObjectVN>> pendingread;

    /*
     * A locktable for object-level lock.
     */
    private ObjectLockTable locktable;

    /*
     * The collection of all workers.
     */
    private Collection<Worker> workers;

    private int numAbortLock;
    private int numAbortVc;

    /**
     * Create a new instance of this class.
     *
     * @param buffer A buffer to use for transactions with pending dependencies.
     *               This should be an empty buffer.
     */
    public StoreSB(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new ConcurrentHashMap<>();
        this.pendingread = new ConcurrentHashMap<>();
        this.locktable = new ObjectLockTable();

        this.numAbortLock = 0;
        this.numAbortVc = 0;
    }

    public StoreSB(SmartBuffer buffer, HashMap<Long, Long> lastversion){
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new ConcurrentHashMap<>();
        this.pendingread = new ConcurrentHashMap<>();
        this.locktable = new ObjectLockTable();

        this.numAbortLock = 0;
        this.numAbortVc = 0;

        this.lastversion = lastversion;
    }

    @Override
    public void setWorkers(Collection<Worker> workers) {
        this.workers = workers;
    }

    @Override
    public Future<Boolean> prepare(Worker worker, long tid, Set<ObjectVN> reads, Set<ObjectVN> writes) {
        // Check version conflict
        Set<ObjectVN> actualdeps = new HashSet<>();
        Set<ObjectVN> versionconflict = new HashSet<>();

        if (reads == null){
            reads = new HashSet<>();
        }

        if (writes == null){
            writes = new HashSet<>();
        }

        for (ObjectVN object : reads) {
            // if there is a older version, there is a version conflict and prepare fails.
            if (object.vnum < lastversion.getOrDefault(object.oid, 0l)) {
                versionconflict.add(new ObjectVN(object.oid, lastversion.get(object.oid)));
                // if there is a version that's never seen, add that to the dependency of this transaction
            } else if (object.vnum > lastversion.get(object.oid)) {
                actualdeps.add(object);
            }
        }

        if (!versionconflict.isEmpty()) {
            worker.update(versionconflict);
            numAbortVc++;
            return futureWith(false);
        }

        pending.put(tid, new HashSet<>(writes));
        pendingread.put(tid, new HashSet<>(reads));

        if (actualdeps.isEmpty()) {
            // Grab the lock on the store's side
            boolean res = locktable.grabLock(reads, writes, tid);
            if (!res) {
                numAbortLock++;
            }
            return futureWith(res);
        } else {
            // Result resolved to true if the dependencies of [tid] are resolved. resolved to false only when there is version conflict
            return buffer.add(tid, reads);
        }
    }

    @Override
    public void commit(Worker worker, long tid) {
        if (!pending.containsKey(tid)){
            return;
        }
        for (ObjectVN write : pending.get(tid)) {
            lastversion.put(write.oid, write.vnum);

            // notify all other workers if this is a create
            if (write.vnum == 0) {
                for (Worker w : workers) {
                    if (w != worker) {
                        w.addObject(this, write);
                    }
                }
            }

            // Remove object from the buffer
            buffer.remove(write);
        }
        // Release Lock
        locktable.releaseLock(pendingread.getOrDefault(tid, new HashSet<>()), pending.getOrDefault(tid, new HashSet<>()), tid);
        pending.remove(tid);
        pendingread.remove(tid);
    }

    @Override
    public void abort(long tid) {
        pending.remove(tid);
        pendingread.remove(tid);
        buffer.delete(tid);
    }

    /**
     * Create an already completed future with a given value.
     *
     * @param value The value.
     * @param <T> The type of the value (and the future).
     * @return An already completed future with the value {@code value}.
     */
    private <T> Future<T> futureWith(T value) {
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public Long getVersion(long oid) {
        return lastversion.get(oid);
    }

    @Override
    public boolean grabLock(long tid) {
        return locktable.grabLock(pendingread.get(tid), pending.get(tid), tid);
    }
    
    @Override
    public void setversion(ObjectVN object) {
        this.lastversion.put(object.oid, object.vnum);
    }
    
    @Override
    public void addpending(long tid) {
        pendingread.put(tid, new HashSet<>());
        pending.put(tid, new HashSet<>());
    }

    @Override
    public int pending() {
        return pending.size();
    }

    @Override
    public Set<Long> pendingkey(){
        return pending.keySet();
    }

    @Override
    public int numLink(){
        return buffer.numLink();
    }

    @Override
    public String toString() {
        return String.format("Store has %d pending transactions %s and %d transactions in buffer. Store aborted " +
                "%d txns because of a lock conflict and %d txns because of a version conflict. "
                + buffer.toString(), pending(), pendingkey(), numLink(), numAbortLock, numAbortVc);
    }

    public int getNumAborts() {
        return numAbortLock + numAbortVc;
    }

    public int getNumAbortLock() {
        return numAbortLock;
    }

    public int getNumAbortVc() {
        return numAbortVc;
    }
}
