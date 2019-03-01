package util;

public enum TxnState {
    /*
     * Preparation of the transaction has started.
     */
    Preparing,
    
    /*
     * Preparation of the transaction has ended.
     */
    Prepared,
    
    /*
     * Committing of the transaction has started.
     */
    Committing,
    
    /*
     * Committing of the transaction has ended.
     */
    Committed,
    
    /*
     * Aborting of the transaction has started.
     */
    Aborting,
    
    /*
     * Aborting of the transaction has ended.
     */
    Aborted
}
