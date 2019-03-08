package smartbuffer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.google.common.base.Function;
import java.util.function.Supplier;

import util.ObjectVN;

public interface SmartBuffer {
    /**
     * Add a transaction with a set of dependencies to the buffer. This method will return a {@code Future} that
     * resolves with {@code true} if the transaction prepares successfully, and {@code false} if there is something that
     * prevents the transaction from being prepared such as a version conflict. A transaction is viewed as resolved if
     * all of its dependencies are resolved.
     *
     * Note that if a transaction is deleted from the buffer before its corresponding future resolves, the future will
     * never resolve.
     *
     * @param tid The ID of the transaction.
     * @param deps A set of <i>all</i> of the transaction's dependencies. It is important that resolved dependencies are
     *             also included, since they may become unresolved at some point in the future.
     * @return A {@code Future} that resolves in accord with the transaction dependency status.
     */
    Future<Boolean> add(long tid, Set<ObjectVN> deps);

    /**
     * Remove a dependency from the dependencies of any transactions that rely on it.
     *
     * @param object The dependency.
     */
    void remove(ObjectVN object);

    /*
     * Eject transactions that depends on [object] with a smaller [vnum].
     * Return a list of transactions ejected.
     */

    /**
     * Eject transactions that have a version conflict with a given dependency. These transactions will be dropped.
     *
     * @param object The dependency.
     */
    void eject(ObjectVN object);

    /**
     * Remove a transaction from the buffer. Note that this will prevent the future that was returned from adding the
     * transaction from ever resolving.
     *
     * @param tid The ID of the transaction.
     */
    void delete(long tid);
}
