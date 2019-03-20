package util;

import java.util.ArrayList;
import java.util.concurrent.*;

import smartbuffer.NumLinkBuffer;
import smartbuffer.SmartBuffer;

public class HardCodedTest {
    /*
     * Number of stores.
     */
    private static final int STORE_NUM = 1;
    
    /*
     * Number of workers.
     */
    private static final int WORKER_NUM = 1;
    
    /*
     * Intervals for Transaction Generators to generate new transaction.
     */
    private static final int NEW_TRANS_INV = 1;
    
    /*
     * 
     */
    private static final int QUEUE_CAP = 10;
    
    /*
     * List of stores.
     */
    private ArrayList<Store> storelist;
    
    /*
     * List of queues.
     */
    private ArrayList<BlockingQueue<Txn>> queuelist;
    
    public void main(String args[]) {
        //Initialize stores
        for (int i = 0; i < STORE_NUM; i++) {
            SmartBuffer buffer = new NumLinkBuffer();
            Store store = new StoreSB(buffer);
            storelist.add(store);
        }
        
        //Initialize workers
        for (int i = 0; i < WORKER_NUM; i++) {
            Worker worker = new Worker(i,storelist,false);
            BlockingQueue<Txn> queue = new ArrayBlockingQueue<Txn>(QUEUE_CAP);
            worker.setqueue(queue);
            queuelist.add(queue);
        }
    }
}
