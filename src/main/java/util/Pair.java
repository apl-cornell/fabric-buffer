package util;

public class Pair<A, B> {
    private A first;
    private B second;

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }
}
