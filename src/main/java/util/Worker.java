package util;

public interface Worker {
    public void update(ObjectVN object);
    public void addPrepared(Txn t);
}
