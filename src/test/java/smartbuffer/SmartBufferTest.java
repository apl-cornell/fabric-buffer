package smartbuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.StoreSB;

import java.util.HashSet;

abstract class SmartBufferTest {
    private SmartBuffer buffer;

    abstract SmartBuffer createInstance();

    @Test
    void simpleTest() {
        buffer.add(5, new HashSet<>());
        // just make sure add works
    }

    @BeforeEach
    void setUp() {
        buffer = createInstance();
        buffer.setStore(new StoreSB(buffer));
    }

    @AfterEach
    void tearDown() {
    }
}