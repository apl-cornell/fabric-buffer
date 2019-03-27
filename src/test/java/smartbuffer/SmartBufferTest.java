package smartbuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.ObjectVN;
import util.Store;
import util.StoreSB;

import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class SmartBufferTest {
    private SmartBuffer buffer;
    private Store store;

    abstract SmartBuffer createInstance();

    @Test
    void simpleTest() {
        int tid = 5;
        store.addpending(tid);
        buffer.add(tid, new HashSet<>());
        // just make sure add works
    }
    
    @Test
    void removeTest() {
        store.setversion(new ObjectVN(1,1));
        store.setversion(new ObjectVN(2,1));

        HashSet<ObjectVN> deps1 = new HashSet<>();
        deps1.add(new ObjectVN(1,1));
        deps1.add(new ObjectVN(2,1));

        store.addpending(1);
        Future<Boolean> future1 = buffer.add(1, deps1);

        HashSet<ObjectVN> deps2 = new HashSet<>();
        deps2.add(new ObjectVN(2, 2));
        store.addpending(2);
        Future<Boolean> future2 = buffer.add(2, deps2);

        store.setversion(new ObjectVN(2, 2));
        buffer.remove(new ObjectVN(2, 2));

        try {
            boolean res1 = future1.get();
            boolean res2 = future2.get();
            assertTrue(res1);
            assertTrue(res2);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    void ejectTest() {
        store.setversion(new ObjectVN(1,0));
        store.setversion(new ObjectVN(2,1));

        HashSet<ObjectVN> deps1 = new HashSet<>();
        deps1.add(new ObjectVN(1,1));
        deps1.add(new ObjectVN(2,1));

        store.addpending(1);
        Future<Boolean> future1 = buffer.add(1, deps1);

        HashSet<ObjectVN> deps2 = new HashSet<>();
        deps2.add(new ObjectVN(2, 2));
        store.addpending(2);
        Future<Boolean> future2 = buffer.add(2, deps2);

        store.setversion(new ObjectVN(2, 2));
        buffer.remove(new ObjectVN(2, 2));

        try {
            boolean res1 = future1.get();
            boolean res2 = future2.get();
            assertFalse(res1);
            assertTrue(res2);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @BeforeEach
    void setUp() {
        buffer = createInstance();
        store = new StoreSB(buffer);
        buffer.setStore(store);
    }
    

    @AfterEach
    void tearDown() {
    }
}