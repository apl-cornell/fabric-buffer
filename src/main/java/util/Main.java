package util;

import java.util.ArrayList;
import smartbuffer.*;

public class Main {
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
     * Whether worker prepare transactions concurrently.
     */
    private static final boolean WORKER_CONCUR_PREPARE = false; 
    
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
            Worker worker = new Worker(i, storelist, WORKER_CONCUR_PREPARE);
            TxnGenerator txngen = new TxnGenerator(worker);
            workerlist.add(worker);
            txngenlist.add(txngen);
        }
    }
    
    /*
     * Threads for transaction generators to create transaction.
     */
    class TxnGenThread implements Runnable {
        private TxnGenerator txngen;
        
        public TxnGenThread(TxnGenerator txngen) {
            this.txngen = txngen;
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            txngen.newTxn();
            try {
                Thread.sleep(NEW_TRANS_INV);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    /*
     * Threads for transaction prepare.
     */
    class WorkerPrepareThread implements Runnable {
        private Worker worker;
        
        public WorkerPrepareThread(Worker worker) {
            this.worker = worker;
        }

        @Override
        public void run() {
            worker.startnewtxn();
        }
    }
    
    /*
     * Thread for transaction commit.
     */
    class WorkerCommitThread implements Runnable {
        private Worker worker;
        
        public WorkerCommitThread(Worker worker) {
            this.worker = worker;
        }
        
        @Override
        public void run() {
            worker.
        }
    }
}
