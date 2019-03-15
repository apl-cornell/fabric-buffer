package smartbuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

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
    }

    @AfterEach
    void tearDown() {
    }
}