package smartbuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import util.ObjectVN;
import util.Store;
import util.StoreSB;

import java.util.HashSet;
import java.util.concurrent.Future;

abstract class SmartBufferTest {
    private SmartBuffer buffer;
    private Store store;

    abstract SmartBuffer createInstance();

    @Test
    void simpleTest() {
        buffer.add(5, new HashSet<>());
        // just make sure add works
    }
    
    @Test
    void ejectTest() {
        store.setversion(new ObjectVN(2,1));
        
        HashSet<ObjectVN> deps1 = new HashSet<>();
        deps.add(new ObjectVN(1,1));
        deps.add(new ObjectVN(2,1));
        Future<Boolean> future1 = buffer.add(1, deps1);
        
        HashSet<ObjectVN> deps2 = new HashSet<>();
        deps2.add(new ObjectVN(2, 2));
        Future<Boolean> future2 = buffer.add(2, deps2);
        
        store.addpending(2);
        store.setversion(new ObjectVN(2, 2));
        buffer.remove(new ObjectVN(2, 2));
        
        boolean res1 = future1.get();
        boolean res2 = future2.get();
        System.out.println(res1);
        System.out.println(res2);
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