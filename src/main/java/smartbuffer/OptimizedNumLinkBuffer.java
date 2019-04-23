package smartbuffer;

import util.ObjectVN;
import util.Store;
import util.Util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OptimizedNumLinkBuffer implements SmartBuffer {
    /*
     * A map from the object to transaction IDs that depend on the object.
     */
    private ConcurrentHashMap<ObjectVN, HashSet<Long>> depsMap;

    /*
     * A map from the object to transaction IDS that depend on the object and that the dependency is not resolved.
     */
    private ConcurrentHashMap<ObjectVN, Long> unresolveddepsMap;

    /*
     * A map from a transaction ID to the number of unresolved dependencies.
     * The keys are synchronized with {@code futures}.
     */
    private HashMap<Long, Integer> numLink;

    /*
     * A map from oid to the associated lock.
     */
    private ConcurrentHashMap<Long, Lock> objlocktable;

    /*
     * A map from tid to the associated lock
     */
    private ConcurrentHashMap<Long, Lock> txnlocktable;

    /*
     * A map from transactions in the buffer to associated futures to be filled.
     * The keys are synchronized with {@code numLink}.
     */
    private HashMap<Long, CompletableFuture<Boolean>> futures;

    /*
     * A pointer to the store that the buffer is associated with.
     */
    public Store store;

    private int num_abort_lock;
    private int num_abort_vc;
    private int num_resolve;




    public OptimizedNumLinkBuffer() {
        depsMap = new ConcurrentHashMap<>();
        unresolveddepsMap = new ConcurrentHashMap<>();
        numLink = new HashMap<>();
        objlocktable = new ConcurrentHashMap<>();
        txnlocktable = new ConcurrentHashMap<>();
        futures = new HashMap<>();

        num_abort_lock = 0;
        num_abort_vc = 0;
    }

    private Lock getObjLock(Long oid) {
        Lock lock = new ReentrantLock();
        Lock existing = objlocktable.putIfAbsent(oid, lock);
        return existing == null? lock : existing;
    }

    private Lock getTxnLock(Long tid) {
        Lock lock = new ReentrantLock();
        Lock existing = txnlocktable.putIfAbsent(tid, lock);
        return existing == null? lock : existing;
    }

    @Override
    public Future<Boolean> add(long tid, Set<ObjectVN> deps) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        synchronized (getTxnLock(tid)) {
            numLink.put(tid, 0);
            futures.put(tid, future);
        }
        for (ObjectVN object : deps) {
            synchronized (getObjLock(object.oid)) {
                if (store.getVersion(object.oid) > object.vnum) {
                    //Version Conflict
                    synchronized (getTxnLock(tid)){
                        numLink.remove(tid);
                        futures.remove(tid);
                        future.complete(false);
                        num_abort_vc++;
                        return future;
                    }
                } else if (store.getVersion(object.oid) < object.vnum) {
                    unresolveddepsMap.put(object, tid);
                    Util.addToSetMap(depsMap, object, tid);
                    synchronized (getTxnLock(tid)) {
                        //if the transaction is not aborted
                        if (numLink.containsKey(tid)) {
                            numLink.put(tid, numLink.get(tid) + 1);
                        } else {
                            break; // transaction has been aborted
                        }
                    }
                } else {
                    Util.addToSetMap(depsMap, object, tid);
                }
            }
        }
        synchronized (getTxnLock(tid)) {
            if (numLink.containsKey(tid)) {
                if (numLink.get(tid) == 0) {
                    numLink.remove(tid);
                    futures.remove(tid);
                    boolean res = store.grabLock(tid);
                    if (!res) {
                        num_abort_lock++;
                    } else {
                        num_resolve++;
                    }
                    future.complete(res);
                }
            }
        }
        return future;
    }

    @Override
    public void remove(ObjectVN object) {
        eject(object);
        synchronized (getObjLock(object.oid)) {
            //If the current version of object is not ejected from the buffer
            if (unresolveddepsMap.containsKey(object)) {
                long tid = unresolveddepsMap.get(object);
                //If [tid] is not ejected from the buffer
                synchronized (getTxnLock(tid)) {
                    if (numLink.containsKey(tid)) {
                        numLink.put(tid, numLink.get(tid) - 1);
                        if (numLink.get(tid) == 0) {
                            numLink.remove(tid);
                            boolean res = store.grabLock(tid);
                            if (!res) {
                                num_abort_lock++;
                            } else {
                                num_resolve++;
                            }
                            futures.get(tid).complete(res);
                            futures.remove(tid);
                        }
                    }
                }
            }
            unresolveddepsMap.remove(object);
            depsMap.remove(object);
        }
    }

    @Override
    public void eject(ObjectVN object) {
        synchronized (getObjLock(object.oid)) {
            ObjectVN to_remove = new ObjectVN(object.oid, object.vnum - 1);
            if (depsMap.containsKey(to_remove)){
                for (long tid : depsMap.get(to_remove)){
                    synchronized (getTxnLock(tid)) {
                        if (numLink.containsKey(tid)){
                            numLink.remove(tid);
                            futures.get(tid).complete(false);
                            num_abort_vc++;
                            futures.remove(tid);
                        }
                    }
                }
                depsMap.remove(to_remove);
            }
        }
    }

    @Override
    public void delete(long tid) {
        synchronized (getTxnLock(tid)) {
            numLink.remove(tid);
            if (futures.containsKey(tid)){
                futures.remove(tid).complete(false);
            }
        }
    }

    @Override
    public void setStore(Store store) {
        this.store = store;
    }

    @Override
    public int numLink() {
        return numLink.size();
    }

    @Override
    public String toString() {
        return String.format("Buffer aborted %d txns because locking and %d txns because vc and resolved %d txns", num_abort_lock, num_abort_vc, num_resolve);
    }
}
