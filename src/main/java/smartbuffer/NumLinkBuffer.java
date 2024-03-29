package smartbuffer;

import util.ObjectVN;
import util.Store;
import util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NumLinkBuffer implements SmartBuffer {
    /*
     * A map from the object to transaction IDs that depend on the object.
     */
    private HashMap<ObjectVN, HashSet<Long>> depsMap;
    
    /*
     * A map from the object to transaction IDS that depend on the object and that the dependency is not resolved.
     */
    private HashMap<ObjectVN, HashSet<Long>> unresolveddepsMap;

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
     * The keys are synchronized with {@code getNumLink}.
     */
    private HashMap<Long, CompletableFuture<Boolean>> futures;
    
    /*
     * A pointer to the store that the buffer is associated with.
     */
    public Store store;

    private int num_abort_lock;
    private int num_abort_vc;
    private int num_resolve;
    
    
    public NumLinkBuffer() {
        // TODO: decide the implementation we want to use
        // Look at performance considerations, as well as whether we care about value ordering or not
        depsMap = new HashMap<>();
        unresolveddepsMap = new HashMap<>();
        numLink = new HashMap<>();
        objlocktable = new ConcurrentHashMap<>();
        txnlocktable = new ConcurrentHashMap<>();
        futures = new HashMap<>();

        num_abort_lock = 0;
        num_abort_vc = 0;
        num_resolve = 0;
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
    //TODO : Check whether holding 1 objlock and 1 txnlock causes deadlock.
    public synchronized Future<Boolean> add(long tid, Set<ObjectVN> deps) {
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
                    Util.addToSetMap(unresolveddepsMap, object, tid);
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
                    boolean res = store.grabLock(tid);
                    if (!res) {
                        num_abort_lock++;
                    } else {
                        num_resolve++;
                    }
                    future.complete(res);
                } else {
                    futures.put(tid, future);
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
                for (long tid : unresolveddepsMap.get(object)) {
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
            }
        }
    }

    @Override
    public synchronized void eject(ObjectVN object) {
        synchronized (getObjLock(object.oid)) {
            LinkedList<ObjectVN> to_remove = new LinkedList<>();
            for (ObjectVN object_curr : depsMap.keySet()) {
                if (object_curr.older(object)) {
                    for (long tid : depsMap.get(object_curr)) {
                        synchronized(getTxnLock(tid)) {
                            if (numLink.containsKey(tid)) {
                                numLink.remove(tid);
                                futures.get(tid).complete(false);
                                num_abort_vc++;
                                futures.remove(tid);
                            }
                        }
                    }
                    to_remove.add(object_curr);
                    unresolveddepsMap.remove(object_curr);
                }
            }
            for (ObjectVN object_curr : to_remove) {
                depsMap.remove(object_curr);
            }
        }
    }
    
    @Override
    public void delete(long tid) {
        // TODO: check if tid is in the map
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
    public int getNumLink(){
        return numLink.size();
    }

    @Override
    public int getNumResolve() {
        return num_resolve;
    }

    @Override
    public int getNumAbortLock() {
        return num_abort_lock;
    }

    @Override
    public int getNumAbortVC() {
        return num_abort_vc;
    }

    @Override
    public String toString() {
        return String.format(
                "Buffer resolved %d txns, aborted %d txns because of a lock conflict, %d txns because of a version conflict",
                num_resolve, num_abort_lock, num_abort_vc
        );
    }
}
