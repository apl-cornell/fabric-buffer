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
     * Whether worker prepare and commit transactions concurrently.
     * 
     * If true, worker prepares for a transaction and if the prepare is 
     * successful, worker commit the transaction immediately. Only one 
     * transaction is being prepared.
     * 
     * If false, worker prepare a transaction in a separate thread and commit
     * a transaction in a separate thread.
     */
    private static final boolean WORKER_CONCUR = false; 
    
    /*
     * List of stores.
     */
    private ArrayList<Store> storelist;
    
    /*
     * List of worker prepare thread.
     */
    private ArrayList<Thread> workerpreparelist;
    
    /*
     * List of worker commit thread.
     */
    private ArrayList<Thread> workercommitlist;
    
    /*
     * List of Transaction Generators.
     */
    private ArrayList<Thread> txngenlist;
    
    
    public void main(String args[]) {
        //Initialize stores
        for (int i = 0; i < STORE_NUM; i++) {
            SmartBuffer buffer = new NumLinkBuffer();
            Store store = new StoreSB(buffer);
            storelist.add(store);
        }
        
        //Initialize workers
        for (int i = 0; i < WORKER_NUM; i++) {
            Worker worker = new Worker(i, storelist, WORKER_CONCUR);
            TxnGenerator txngen = new TxnGenerator(worker);
            if (WORKER_CONCUR) {
                workercommitlist.add(new Thread(new WorkerPrepareThread(worker)));
            }
            workerpreparelist.add(new Thread(new WorkerPrepareThread(worker)));
            txngenlist.add(new Thread(new TxnGenThread(txngen)));
        }
        
        //Start txngen thread and worker thread
        for (int i = 0; i < WORKER_NUM; i++) {
            txngenlist.get(i).start();
            workerpreparelist.get(i).start();
            if (WORKER_CONCUR) {
                workercommitlist.get(i).start();
            }
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
            worker.committxn();
        }
    }
}
