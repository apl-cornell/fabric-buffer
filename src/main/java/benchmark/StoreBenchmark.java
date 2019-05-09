package benchmark;

public class StoreBenchmark implements CSVData {
    private int pending;
    private int inBuffer;
    private int abortedLock;
    private int abortedVC;
    private int bufferResolved;
    private int bufferAbortedLock;
    private int bufferAbortedVC;

    public StoreBenchmark(int pending,
                          int inBuffer,
                          int abortedLock,
                          int abortedVC,
                          int bufferResolved,
                          int bufferAbortedLock,
                          int bufferAbortedVC) {
        this.pending = pending;
        this.inBuffer = inBuffer;
        this.abortedLock = abortedLock;
        this.abortedVC = abortedVC;
        this.bufferResolved = bufferResolved;
        this.bufferAbortedLock = bufferAbortedLock;
        this.bufferAbortedVC = bufferAbortedVC;
    }

    public static String[] header() {
        return new String[] {
                "Pending",
                "InBuffer",
                "AbortedLock",
                "AbortedVC",
                "BufferResolved",
                "BufferAbortedLock",
                "BufferAbortedVC"
        };
    }

    @Override
    public String[] row() {
        return new String[] {
                Integer.toString(pending),
                Integer.toString(inBuffer),
                Integer.toString(abortedLock),
                Integer.toString(abortedVC),
                Integer.toString(bufferResolved),
                Integer.toString(bufferAbortedLock),
                Integer.toString(bufferAbortedVC)
        };
    }
}
