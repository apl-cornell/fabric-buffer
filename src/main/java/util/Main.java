package util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

import smartbuffer.*;

public class Main {
    /*--------------------------------------General Configuration-------------------------------------*/
    /**
     * Number of stores.
     */
    private static final int STORE_NUM = 2;
    
    /**
     * Number of workers.
     */
    private static final int WORKER_NUM = 1;

    /**
     * Duration of the test.
     */
    private static final int DURATION = 10000;

    /**
     * Initial number of objects per store.
     */
    private static final int INITIAL_CAPACITY = 1000000;

    /*--------------------------------------Worker Configuration--------------------------------------*/
    /**
     * Number of worker threads
     */
    private static final int NUM_THREAD = 8;

    /**
     * Time interval for communicating with stores other than the home store.
     */
    private static final int NON_HOME_INV = 0;

    /**
     * Time interval for communicating with the home store.
     */
    private static final int HOME_INV = 0;

    /**
     * Intervals for worker to start a new txn. Only used when WORKER_CONCUR is
     * true.
     */
    private static final int TRANS_PREPARE_INV = 0;

    /**
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

    /**
     * If true, run the testing with original 2PC protocol.
     */
    private static final boolean ORIGINAL = false;

    /*-----------------------------------Txn Generator Configuration-----------------------------------*/
    /**
     * Intervals for Transaction Generators to generate new transaction.
     */
    private static final int NEW_TRANS_INV = 0;

    /**
     * Size of the blocking queue between each worker and the associated txn generator.
     */
    private static final int TXN_QUEUE_CAPACITY = 10;

    /**
     * If true, each worker is associated with an ordinary txn generator and another test txn generator which
     * generates fixed size txns. The size can be modified (TxnGenerator.java)
     */
    private static final boolean TxnGen_Test = false;

    /*----------------------------------Control fields------------------------------------------*/
    /*
     * End flag of the whole testing
     */
    private boolean exit;

    private int txn_ended;

    private AtomicLong last_unused_oid;
    
    
    private void newTest(String[] args) {
        //Initialize fields
        // List of stores.
        ArrayList<Store> storelist = new ArrayList<>();
        // List of worker prepare thread.
        ArrayList<Thread> workerpreparelist = new ArrayList<>();
        // List of Transaction Generators.
        ArrayList<Thread> txngenlist = new ArrayList<>();
        // List of txngentest thread.
        ArrayList<Thread> txngentestlist = new ArrayList<>();

        last_unused_oid = new AtomicLong();


        HashMap<Long, Long> lastversion = new HashMap<>();
        //Initialize stores
        for (int i = 0; i < STORE_NUM; i++) {
            //Initialize objects
            HashMap<Long, Long> lastversion_store = new HashMap<>();
            for (long oid = i*INITIAL_CAPACITY; oid < (i + 1)*INITIAL_CAPACITY; oid++){
                last_unused_oid.incrementAndGet();
                lastversion.put(oid, 0L);
                lastversion_store.put(oid, 0L);
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
            int storeindex = (int)(1.0 * i / WORKER_NUM * STORE_NUM);
            Worker worker = new Worker(i, storelist, WORKER_CONCUR, ORIGINAL, lastversion, location, NUM_THREAD, storelist.get(storeindex), HOME_INV, NON_HOME_INV);
            workerlist.add(worker);
            TxnGenerator txngen;
            txngen = new TxnGenerator(worker, RandomGenerator.constant(0.001f), 0.1f, TXN_QUEUE_CAPACITY, last_unused_oid);

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
        
        TxnGenThread(TxnGenerator txngen) {
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
            }
        }
    }

    class TxnGenTestThread implements Runnable {
        private TxnGenerator txngen;

        TxnGenTestThread(TxnGenerator txngen) { this.txngen = txngen; }

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
        
        WorkerPrepareThread(Worker worker) {
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

    public static void main(String[] args) {
        (new Main()).newTest(args);
    }
}
