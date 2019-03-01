package smartbuffer;

import java.util.*;
import java.util.concurrent.Future;

import com.google.common.base.Function;
import java.util.function.Supplier;

import util.ObjectVN;

public interface SmartBuffer {
    /*
     * Add transaction [tid] that depends on objects in [deps] to the buffer
     * Return true if the add is success.
     */
    Future<Boolean> add(long tid, Set<ObjectVN> deps);

    /*
     * Remove [object] from the deps of transactions depend on [oid].
     * Return a list of transactions with no deps.
     */
    List<Long> remove(ObjectVN object);

    /*
     * Eject transactions that depends on [object] with a smaller [vnum].
     * Return a list of transactions ejected.
     */
    List<Long> eject(ObjectVN object);
    
    /*
     * Remove transaction [tid] from the buffer.
     */
    void delete(long tid);
}
