package util;

public class ObjectVN {
    /**
     * The object's ID.
     */
    public long oid;

    /**
     * The object's version number.
     */
    public long vnum;

    /**
     * Create a new object with a given ID and version number.
     *
     * @param oid The object ID.
     * @param vnum The version number.
     */
    public ObjectVN(long oid, long vnum) {
        this.oid = oid;
        this.vnum = vnum;
    }

    /**
     * Check if this object has an older version than another view of the same
     * object.
     *
     * @param object The object to compare to.
     * @return {@code true} if the two objects have the same ID, and the current
     * instance has a smaller version number than {@code object}, and
     * {@code false} otherwise.
     */
    public boolean older(ObjectVN object) {
        return (this.oid == object.oid && this.vnum < object.vnum);
    }

    @Override
    public String toString() {
        return String.format("Object %d: v%d", oid, vnum);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ObjectVN) {
            ObjectVN objectVN = (ObjectVN) o;
            return this.oid == objectVN.oid && this.vnum == objectVN.vnum;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = (int) (oid ^ (oid >>> 32));
        result = 31 * result + (int) (vnum ^ (vnum >>> 32));
        return result;
    }
}
