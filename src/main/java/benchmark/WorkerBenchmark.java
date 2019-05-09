package benchmark;

public class WorkerBenchmark implements CSVData {
    private int completed;
    private int aborted;
    private int abortedLock;

    public static String[] header() {
        return new String[] {"Completed", "Aborted", "AbortedLock"};
    }

    public WorkerBenchmark(int completed, int aborted, int abortedLock) {
        this.completed = completed;
        this.aborted = aborted;
        this.abortedLock = abortedLock;
    }

    @Override
    public String[] row() {
        return new String[] {
                Integer.toString(completed),
                Integer.toString(aborted),
                Integer.toString(abortedLock)
        };
    }
}
