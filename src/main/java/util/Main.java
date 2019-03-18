package util;

import java.util.ArrayList;
import smartbuffer.*;

public class Main {
    private static final int STORE_NUM = 1;
    private static final int WORKER_NUM = 1;
    
    /*
     * List of stores.
     */
    private ArrayList<Store> storelist;
    
    /*
     * List of workers.
     */
    private ArrayList<Worker> workerlist;
    
    /*
     * List of Transaction Generators.
     */
    private ArrayList<TxnGenerator> txngenlist;
    
    
    public void main(String args[]) {
        //Initialize stores
        for (int i = 0; i < STORE_NUM; i++) {
            SmartBuffer buffer = new NumLinkBuffer();
            Store store = new StoreSB(buffer);
            storelist.add(store);
        }
        
        //Initialize workers
        for (int i = 0; i < WORKER_NUM; i++) {
            Worker worker = new Worker(i, storelist);
            TxnGenerator txngen = new TxnGenerator(worker);
        }
    }
}
