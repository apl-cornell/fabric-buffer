package util;

public class ObjectVN {
    /*
     * Unique identifier of the object
     */
    public long oid;

    /*
     * Version number of the object
     */
    public long vnum;

    public ObjectVN(long oid, long vnum){
        this.oid = oid;
        this.vnum = vnum;
    }

    public boolean older(ObjectVN object) {
        return (this.oid == object.oid && this.vnum < object.vnum);
    }
}
