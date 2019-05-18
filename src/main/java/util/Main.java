package util;

import benchmark.CSVData;
import benchmark.StoreBenchmark;
import benchmark.WorkerBenchmark;
import picocli.CommandLine;
import smartbuffer.OptimizedNumLinkBuffer;
import smartbuffer.SmartBuffer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@CommandLine.Command (name = "fbuffer", version = "Fabric buffer tester")
public class Main implements Runnable {
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
    private static final int INITIAL_CAPACITY = 10000;

    /*--------------------------------------Store Configuration--------------------------------------*/
    /**
     * If true, run the store with smart buffer. If false, run the store without smart buffer.
     * WITH_BUFFER = false implies ORIGINAL = true.
     */
    private static final boolean WITH_BUFFER = true;

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
    private static final boolean ORIGINAL = true;

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

    /**
     * Run a test, generating and running transactions for the set duration.
     *
     * @param duration Time to run the simulation for (in milliseconds).
     * @param stores The number of stores.
     * @param workers The number of workers.
     * @param threads The number of threads <b>per worker</b>.
     * @param dbSize The number of objects <b>per store</b>.
     * @param txnSize The number of objects in each transaction, as a proportion of the total number of objects in each
     *                store.
     * @param writeRatio The proportion of queried objects that are writes.
     */
    private Pair<List<Store>, List<Worker>> newTest(int duration,
                         int stores,
                         int workers,
                         int threads,
                         int dbSize,
                         RandomGenerator txnSize,
                         float writeRatio) {
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
        for (int i = 0; i < stores; i++) {
            //Initialize objects
            HashMap<Long, Long> lastversion_store = new HashMap<>();
            for (long oid = i*dbSize; oid < (i + 1)*dbSize; oid++){
                last_unused_oid.incrementAndGet();
                lastversion.put(oid, 0L);
                lastversion_store.put(oid, 0L);
            }
            SmartBuffer buffer = new OptimizedNumLinkBuffer();
            Store store = new StoreSB(buffer, lastversion_store, WITH_BUFFER);
            buffer.setStore(store);
            storelist.add(store);
        }

        //Initialize location
        HashMap<Long, Store> location = new HashMap<>();
        for (int i = 0; i < stores; i++){
            for (long oid = i*dbSize; oid < (i + 1)*dbSize; oid++){
                location.put(oid, storelist.get(i));
            }
        }

        
        LinkedList<Worker> workerlist = new LinkedList<>();
        //Initialize workers
        for (int i = 0; i < workers; i++) {
            int storeindex = (int)(1.0 * i / workers * stores);
            Worker worker = new Worker(i, storelist, WORKER_CONCUR, ORIGINAL, lastversion, location, threads, storelist.get(storeindex), HOME_INV, NON_HOME_INV);
            workerlist.add(worker);
            TxnGenerator txngen;
            txngen = new TxnGenerator(worker, txnSize, writeRatio, TXN_QUEUE_CAPACITY, last_unused_oid);

            workerpreparelist.add(new Thread(new WorkerPrepareThread(worker)));
            txngenlist.add(new Thread(new TxnGenThread(txngen)));

            if (TxnGen_Test){
                txngentestlist.add(new Thread(new TxnGenTestThread(txngen)));
            }
        }
        
        //Update worker list for each store
        for (int i = 0; i < stores; i++) {
            storelist.get(i).setWorkers(workerlist);
        }

        //Start txngen thread and worker thread
        for (int i = 0; i < workers; i++) {
            txngenlist.get(i).start();
            if (TxnGen_Test){
                txngentestlist.get(i).start();
            }
            workerpreparelist.get(i).start();
        }
        
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        exit = true;

        return new Pair<>(storelist, workerlist);
    }

    // TODO: add custom CSV settings (delimiter etc) as parameter
    private static void printRowToCSV(PrintWriter writer, CSVData data) {
        printRowToCSV(writer, data.row());
    }

    private static void printRowToCSV(PrintWriter writer, String[] row) {
        String rowAsText = String.join(",", row);
        writer.println(rowAsText);
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

    @CommandLine.Option (names = "-O", defaultValue = ".",
            description = "Specify where to place output diagnostic files (default: ${DEFAULT-VALUE})")
    private Path path;

    @CommandLine.Option (names = "-storefile", defaultValue = "stores.csv",
            description = "File name for store benchmarks (default: ${DEFAULT-VALUE})")
    private File storefile;

    @CommandLine.Option (names = "-workerfile", defaultValue = "workers.csv",
            description = "File name for worker benchmarks (default: ${DEFAULT-VALUE})")
    private File workerfile;

    @CommandLine.Option (names = {"-stores"}, defaultValue = "1",
            description = "Number of stores (default: ${DEFAULT-VALUE})")
    private int stores;

    @CommandLine.Option (names = {"-workers"}, defaultValue = "1",
            description = "Number of workers (default: ${DEFAULT-VALUE})")
    private int workers;

    @CommandLine.Option (names = {"-threads"}, defaultValue = "8",
            description = "Number of threads per worker (default: ${DEFAULT-VALUE})")
    private int threads;

    @CommandLine.Option (names = {"-objects"}, defaultValue = "10000",
            description = "Number of objects per store (default: ${DEFAULT-VALUE})")
    private int objects;

    @CommandLine.Option (names = {"-size"}, defaultValue = "0.001",
            description = "Proportion of objects queried per transaction (default: ${DEFAULT-VALUE})")
    private float txnSize;

    @CommandLine.Option (names = {"-writeratio"}, defaultValue = "0.1",
            description = "Proportion of queried objects that are writes (default: ${DEFAULT-VALUE})")
    private float writes;

    @CommandLine.Option (names = {"-time"}, defaultValue = "10000",
            description = "Time to run the simulation for (default: ${DEFAULT-VALUE})")
    private int runtime;

    @CommandLine.Option (names = "-verbose",
            description = "Print benchmark output to the console")
    private boolean verbose = false;

    @Override
    public void run() {
        // testing stuff
        if (!Files.exists(path)) {
            System.err.println("Error: path " + path + " not found");
            return;
        }

        String pathString = path.toString();
        Path storesOutputPath = Paths.get(pathString, storefile.toString());
        Path workersOutputPath = Paths.get(pathString, workerfile.toString());

        try (PrintWriter storesWriter = new PrintWriter(storesOutputPath.toFile());
             PrintWriter workersWriter = new PrintWriter(workersOutputPath.toFile())) {

            Pair<List<Store>, List<Worker>> benchmarks =
                    this.newTest(
                            runtime,
                            stores,
                            workers,
                            threads,
                            objects,
                            RandomGenerator.constant(txnSize),
                            writes
                    );

            List<Store> stores = benchmarks.getFirst();
            List<Worker> workers = benchmarks.getSecond();

            // print benchmarks to sysout if requested
            if (verbose) {
                stores.forEach(System.out::println);
                workers.forEach(System.out::println);
            }

            // to print a list of benchmarks to csv, we first print the header and then print each benchmark's row

            List<StoreBenchmark> storeBenchmarks = stores.stream()
                    .map(Store::getCSVData)
                    .collect(Collectors.toList());
            printRowToCSV(storesWriter, StoreBenchmark.header());
            storeBenchmarks.forEach(benchmark -> printRowToCSV(storesWriter, benchmark));

            List<WorkerBenchmark> workerBenchmarks = workers.stream()
                    .map(Worker::getCSVData)
                    .collect(Collectors.toList());
            printRowToCSV(workersWriter, WorkerBenchmark.header());
            workerBenchmarks.forEach(benchmark -> printRowToCSV(workersWriter, benchmark));
        } catch (IOException e) {
            System.err.println("Unexpected error when creating file output streams: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        CommandLine.run(new Main(), args);
        System.exit(0);
    }
}
