package util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import smartbuffer.SmartBuffer;

import java.util.HashMap;
import java.util.HashSet;
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
    private SetMultimap<Long, ObjectVN> pending;

    /*
     * A map from [tid] to objects that transaction reads for transactions waiting for processing.
     */
    private SetMultimap<Long, ObjectVN> pendingread; // TODO: make sure we add to this at some point

    /*
     * A locktable for object-level lock.
     */
    private ObjectLockTable locktable;

    /**
     * Create a new instance of this class.
     *
     * @param buffer A buffer to use for transactions with pending dependencies.
     *               This should be an empty buffer.
     */
    public StoreSB(SmartBuffer buffer) {
        this.buffer = buffer;
        this.lastversion = new HashMap<>();
        this.pending = new HashMultimap<>();
        this.pendingread = new HashMultimap<>();
        this.locktable = new ObjectLockTable();
    }

    @Override
    public Future<Boolean> prepare(long tid, Set<ObjectVN> reads, Set<ObjectVN> writes) {
        // Check version conflict
        Set<ObjectVN> actualdeps = new HashSet<>();

        for (ObjectVN object : reads) {
            // if there is a older version, there is a version conflict and prepare fails.
            if (object.vnum < lastversion.get(object.oid)) {
                return futurewith(false);
                // if there is a version that's never seen, add that to the dependency of this transaction
            } else if (object.vnum > lastversion.get(object.oid)) {
                actualdeps.add(object);
            }
        }

        pending.putAll(tid, writes);
        if (actualdeps.isEmpty()) {
            // Grab the lock on the store's side
            return futurewith(locktable.grabLock(reads, writes, tid));
        } else {
            // Result resolved to true if the dependencies of [tid] are resolved. resolved to false only when there is version conflict
            return buffer.add(tid, reads);
        }
    }

    @Override
    public void commit(long tid) {
        for (ObjectVN write : pending.get(tid)) {
            lastversion.put(write.oid, write.vnum);
            //Release Lock
            locktable.releaseLock(pendingread.get(tid), pending.get(tid), tid);
            //Remove object from the buffer
            buffer.remove(write);
        }
        pending.removeAll(tid);
    }

    @Override
    public void abort(long tid) {
        pending.removeAll(tid);
        buffer.delete(tid);
    }

    private <T> Future<T> futurewith(T value) {
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public Long getversion(long oid) {
        return lastversion.get(oid);
    }

    @Override
    public boolean grabLock(long tid) {
        return locktable.grabLock(pendingread.get(tid), pending.get(tid), tid);
    }
}
