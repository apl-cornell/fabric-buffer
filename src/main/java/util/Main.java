package util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import smartbuffer.*;

public class Main {
    /*
     * Number of stores.
     */
    private static final int STORE_NUM = 2;
    
    /*
     * Number of workers.
     */
    private static final int WORKER_NUM = 1;
    
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

    private static final boolean TxnGen_Test = false;
    
    /*
     * End flag of the whole testing
     */
    private boolean exit;

    private int txn_ended = 0;
    
    
    public void newTest(String[] args) {
        //Initialize fields
        // List of stores.
        ArrayList<Store> storelist = new ArrayList<>();
        // List of worker prepare thread.
        ArrayList<Thread> workerpreparelist = new ArrayList<>();
        // List of Transaction Generators.
        ArrayList<Thread> txngenlist = new ArrayList<>();
        // List of txngentest thread.
        ArrayList<Thread> txngentestlist = new ArrayList<>();


        HashMap<Long, Long> lastversion = new HashMap<>();
        //Initialize stores
        for (int i = 0; i < STORE_NUM; i++) {
            //Initialize objects
            HashMap<Long, Long> lastversion_store = new HashMap<>();
            for (long oid = i*INITIAL_CAPACITY; oid < (i + 1)*INITIAL_CAPACITY; oid++){
                lastversion.put(oid, 0l);
                lastversion_store.put(oid, 0l);
            }
            SmartBuffer buffer = new OptimizedNumLinkBuffer();
            Store store = new StoreSB(buffer, lastversion_store);
            buffer.setStore(store);
            storelist.add(store);
        }

        //Initialize location
        HashMap<Long, Store> location = new HashMap<>();
        for (int i = 0; i < STORE_NUM; i++){
            for (long oid = i*INITIAL_CAPACITY; oid < (i + 1)*INITIAL_CAPACITY; oid++){
                location.put(oid, storelist.get(i));
            }
        }

        
        LinkedList<Worker> workerlist = new LinkedList<>();
        //Initialize workers
        for (int i = 0; i < WORKER_NUM; i++) {
            Worker worker = new Worker(i, storelist, WORKER_CONCUR, lastversion, location);
            workerlist.add(worker);
            TxnGenerator txngen;
            txngen = new TxnGenerator(worker, RandomGenerator.constant(0.001f), INITIAL_CAPACITY);
            workerpreparelist.add(new Thread(new WorkerPrepareThread(worker)));
            txngenlist.add(new Thread(new TxnGenThread(txngen)));

            if (TxnGen_Test){
                txngentestlist.add(new Thread(new TxnGenTestThread(txngen)));
            }
        }
        
        //Update worker list for each store
        for (int i = 0; i < STORE_NUM; i++) {
            storelist.get(i).setWorkers(workerlist);
        }

        //Start txngen thread and worker thread
        for (int i = 0; i < WORKER_NUM; i++) {
            txngenlist.get(i).start();
            if (TxnGen_Test){
                txngentestlist.get(i).start();
            }
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

    class TxnGenTestThread implements Runnable {
        private TxnGenerator txngen;

        public TxnGenTestThread(TxnGenerator txngen) { this.txngen = txngen; }

        @Override
        public void run() {
            try {
                while (!exit) {
                    txngen.newTestTxn();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
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
