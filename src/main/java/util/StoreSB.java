package util;

import smartbuffer.SmartBuffer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class StoreSB implements Store {
    private SmartBuffer buffer;

    /*
     * A map from an object to the last version of the object that the store has seen.
     */
    private HashMap<Long, Long> lastversion;

    /*
     * A map from [tid] to objects that transaction writes for transactions waiting for processing.
     */
    private HashMap<Long, HashSet<ObjectVN>> pending;

    /*
     * A map from [tid] to objects that transaction reads for transactions waiting for processing.
     */
    private HashMap<Long, HashSet<ObjectVN>> pendingread; // TODO: make sure we add to this at some point

    /*
     * A locktable for object-level lock.
     */
    private ObjectLockTable locktable;

    /*
     * The list of all workers.
     */
    private List<Worker> workerlist;

    /**
     * Create a new instance of this class.
     *
     * @param buffer A buffer to use for transactions with pending dependencies.
     *               This should be an empty buffer.
     */
    public StoreSB(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new HashMap<>();
        this.pendingread = new HashMap<>();
        this.locktable = new ObjectLockTable();
    }

    @Override
    public void setWorkerlist(List<Worker> workerlist) {
        this.workerlist = workerlist;
    }

    @Override
    public Future<Boolean> prepare(Worker worker, long tid, Set<ObjectVN> reads, Set<ObjectVN> writes) {
        // Check version conflict
        Set<ObjectVN> actualdeps = new HashSet<>();
        Set<ObjectVN> versionconflict = new HashSet<>();

        for (ObjectVN object : reads) {
            // if there is a older version, there is a version conflict and prepare fails.
            if (object.vnum < lastversion.get(object.oid)) {
                versionconflict.add(new ObjectVN(object.oid, lastversion.get(object.oid)));
                // if there is a version that's never seen, add that to the dependency of this transaction
            } else if (object.vnum > lastversion.get(object.oid)) {
                actualdeps.add(object);
            }
        }

        if (!versionconflict.isEmpty()) {
            worker.update(versionconflict);
            return futureWith(false);
        }

        Util.addToSetMap(pending, tid, writes);
        if (actualdeps.isEmpty()) {
            // Grab the lock on the store's side
            return futureWith(locktable.grabLock(reads, writes, tid));
        } else {
            // Result resolved to true if the dependencies of [tid] are resolved. resolved to false only when there is version conflict
            return buffer.add(tid, reads);
        }
    }

    @Override
    public void commit(Worker worker, long tid) {
        for (ObjectVN write : pending.get(tid)) {
            lastversion.put(write.oid, write.vnum);

            // notify all other workers if this is a create
            if (write.vnum == 0) {
                for (Worker w : workerlist) {
                    if (w != worker) {
                        w.addObject(this, write);
                    }
                }
            }

            // Remove object from the buffer
            buffer.remove(write);
        }
        // Release Lock
        locktable.releaseLock(pendingread.get(tid), pending.get(tid), tid);
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
}
