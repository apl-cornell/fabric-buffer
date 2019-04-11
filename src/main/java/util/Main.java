package util;

import java.util.ArrayList;
import java.util.LinkedList;

import smartbuffer.*;

public class Main {
    /*
     * Number of stores.
     */
    private static final int STORE_NUM = 1;
    
    /*
     * Number of workers.
     */
    private static final int WORKER_NUM = 2;
    
    /*
     * Intervals for Transaction Generators to generate new transaction.
     */
    private static final int NEW_TRANS_INV = 0;
    
    /*
     * Intervals for worker to start a new txn. Only used when WORKER_CONCUR is
     * true.
     */
    private static final int TRANS_PREPARE_INV = 0;
    
    /*
     * Intervals for worker to commit a txn. Only used when WORKER_CONCUR is 
     * true.
     */
    private static final int TRANS_COMMIT_INV = 1;
    
    /*
     * Whether worker prepare and commit transactions concurrently.
     * 
     * If true, worker prepare a transaction in a separate thread and commit
     * a transaction in a separate thread.
     * 
     * If false, worker prepares for a transaction and if the prepare is 
     * successful, worker commit the transaction immediately. Only one 
     * transaction is being prepared.
     */
    private static final boolean WORKER_CONCUR = true;
    
    /*
     * Duration of the test.
     */
    private static final int DURATION = 10000;
    
    /*
     * Initial capacity of the store of each worker for each store
     */
    private static final int INITIAL_CAPACITY = 10000;
    
    /*
     * End flag of the whole testing
     */
    private boolean exit;
    
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

    private int txn_ended = 0;
    
    
    public void newTest(String[] args) {
        //Initialize fields
        storelist = new ArrayList<>();
        workerpreparelist = new ArrayList<>();
        workercommitlist = new ArrayList<>();
        txngenlist = new ArrayList<>();


        //Initialize stores
        for (int i = 0; i < STORE_NUM; i++) {
            SmartBuffer buffer = new OptimizedNumLinkBuffer();
            Store store = new StoreSB(buffer);
            buffer.setStore(store);
            storelist.add(store);
        }
        
        LinkedList<Worker> workerlist = new LinkedList<>();
        //Initialize workers
        for (int i = 0; i < WORKER_NUM; i++) {
            Worker worker = new Worker(i, storelist, WORKER_CONCUR);
            workerlist.add(worker);
            TxnGenerator txngen;
            txngen = new TxnGenerator(worker, RandomGenerator.constant(0.001f), INITIAL_CAPACITY);
            workerpreparelist.add(new Thread(new WorkerPrepareThread(worker)));
            txngenlist.add(new Thread(new TxnGenThread(txngen)));
        }
        
        //Update worker list for each store
        for (int i = 0; i < STORE_NUM; i++) {
            storelist.get(i).setWorkers(workerlist);
        }

        //Start txngen thread and worker thread
        for (int i = 0; i < WORKER_NUM; i++) {
            txngenlist.get(i).start();
            workerpreparelist.get(i).start();
        }
        
        try {
            Thread.sleep(DURATION);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        exit = true;

        for (Store s : storelist){
            System.out.println(s);
        }

        for (Worker w : workerlist) {
            System.out.println(w);
        }



        System.exit(0);
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
            try {
                while (!exit) {
                    txngen.newTxn();                
                    Thread.sleep(NEW_TRANS_INV);
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                // System.out.println(txngen.txn_created);
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
            if (WORKER_CONCUR) {
                try {
                    while (!exit) {
                        Runnable task = () -> {
                            try {
                                worker.startnewtxn();
                            } catch (Throwable e){
                                System.err.println(e);
                                e.printStackTrace();
                                throw e;
                            }
                            txn_ended++;
                            // System.out.println("Ended " + txn_ended);
                        };
                        worker.pool.execute(task);
                        Thread.sleep(TRANS_PREPARE_INV);
                    } 
                } catch (InterruptedException e) {
                 // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else {
                while (!exit) {
                    worker.startnewtxn();
                }
            }
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
            try {
                while (!exit) {
                    worker.committxn();
                    Thread.sleep(TRANS_COMMIT_INV);
                }
                
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        (new Main()).newTest(args);
    }
}
