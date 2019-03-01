package util;

import java.util.*;
import util.ObjectVN;

public interface Store {
	/*
	 * Prepare for transaction [tid] that reads [reads] and writes [writes].
	 */
	public void prepare(long tid,  List<ObjectVN> reads, List<ObjectVN> writes);
	
	/*
	 * Abort transaction [tid].
	 */
	public void abort(long tid);
	
	/*
	 * Commit transaction [tid].
	 */
	public void commit(long tid);
}
