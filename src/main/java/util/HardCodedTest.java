package util;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.*;

import smartbuffer.NumLinkBuffer;
import smartbuffer.SmartBuffer;
import util.Main.WorkerCommitThread;
import util.Main.WorkerPrepareThread;


/* Read from stdin.
 * The first line specifies the number of provided txns.
 * On each line workerid-(oid vnum store), (oid vmun store)|(oid vnum store), (oid vnum store)
 * The first part before | is the list of reads
 * The second part after | is the list of writes */
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
    
    private static final boolean WORKER_CONCUR = false;
    
    private static final int TRANS_PREPARE_INV = 1;
    
    private static final int TRANS_COMMIT_INV = 1;
    
    /*
     * List of stores.
     */
    private ArrayList<Store> storelist;
    
    /*
     * List of queues.
     */
    private ArrayList<BlockingQueue<Txn>> queuelist;
    
    /*
     * List of workers.
     */
    private ArrayList<Worker> workerlist;
    
    /*
     * Biggest unused Transaction ID
     */
    private long tid = 0;

    private ArrayList<Thread> workercommitlist;

    private ArrayList<Thread> workerpreparelist;
    
    private static boolean exit = false;
    
    public void main(String args[]) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int num_txn = Integer.parseInt(br.readLine());
        
        
        //Initialize stores
        for (int i = 0; i < STORE_NUM; i++) {
            SmartBuffer buffer = new NumLinkBuffer();
            Store store = new StoreSB(buffer);
            storelist.add(store);
        }
        
        //Initialize workers
        for (int i = 0; i < WORKER_NUM; i++) {
            Worker worker = new Worker(i,storelist,WORKER_CONCUR);
            BlockingQueue<Txn> queue = new LinkedBlockingQueue<Txn>();
            worker.setqueue(queue);
            workerlist.add(worker);
            queuelist.add(queue);
            
            if (WORKER_CONCUR) {
                workercommitlist.add(new Thread(new WorkerCommitThread(worker)));
            }
            workerpreparelist.add(new Thread(new WorkerPrepareThread(worker)));
        }
        
        HashMap<Store, HashSet<ObjectVN>> reads = new HashMap<>();
        HashMap<Store, HashSet<ObjectVN>> writes = new HashMap<>();
        
        for (int i = 0; i < num_txn; i++) {
            String str = br.readLine();
            int wid = Integer.parseInt(str.split("-")[0]);
            str = str.split("-")[1];
            String read = str.split("|")[0];
            String write = str.split("|")[1];
            String[] readstr = read.split(",");
            String[] writestr = write.split(",");
            
            for (String s : readstr) {
                s = s.replaceAll("(", "");
                s = s.replaceAll(")", "");
                String[] arg = s.split(" ");
                int sid = Integer.parseInt(arg[0]);
                long oid = Long.parseLong(arg[1]);
                long vnum = Long.parseLong(arg[2]);
                Util.addToSetMap(reads, storelist.get(sid), new ObjectVN(oid, vnum));
            }
            
            for (String s : writestr) {
                s = s.replaceAll("(", "");
                s = s.replaceAll(")", "");
                String[] arg = s.split(" ");
                int sid = Integer.parseInt(arg[0]);
                long oid = Long.parseLong(arg[1]);
                long vnum = Long.parseLong(arg[2]);
                Util.addToSetMap(writes, storelist.get(sid), new ObjectVN(oid, vnum));
            }
            
            Txn ntxn = new Txn(workerlist.get(wid), tid, reads, writes);
            try {
                queuelist.get(wid).put(ntxn);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            tid++;
        }
        
      //Start worker thread
        for (int i = 0; i < WORKER_NUM; i++) {
            workerpreparelist.get(i).start();
            if (WORKER_CONCUR) {
                workercommitlist.get(i).start();
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
                        worker.startnewtxn();
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
}
