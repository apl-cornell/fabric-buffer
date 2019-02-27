package smartbuffer;

import java.util.*;
import util.ObjectVN;

public interface SmartBuffer {
    // TODO: add method specs
	
	/*
	 * Add transaction [tid] that depends on objects in [deps] to the buffer
	 * Return true if the add is success.
	 */
	public boolean add(long tid, Set<ObjectVN> deps);
	
	/*
	 * Remove [object] from the deps of transactions depend on [oid].
	 * Return a list of transactions with no deps.
	 */
	public List<Long> remove(ObjectVN object);
	
	/*
	 * Eject transactions that depends on [object] with a smaller [vnum].
	 * Return a list of transactions ejected.
	 */
	public List<Long> eject(ObjectVN object);
	
}
