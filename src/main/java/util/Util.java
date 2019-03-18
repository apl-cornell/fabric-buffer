package util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Util {
    /**
     * Add a key-value pair to a setmap.
     *
     * @param map The map.
     * @param key The key.
     * @param value The value.
     * @param <T> The type of the key.
     * @param <V> The type of the value.
     */
    public static <T, V> void addToSetMap(Map<T, HashSet<V>> map, T key, V value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            HashSet<V> s = new HashSet<>();
            s.add(value);
            map.put(key, s);
        }
    }

    /**
     * Add a collection of key-value pairs to a setmap.
     *
     * @param map The map.
     * @param key The key.
     * @param values The values.
     * @param <T> The type of the key.
     * @param <V> The type of the values.
     */
    public static <T, V> void addToSetMap(Map<T, HashSet<V>> map, T key, Collection<V> values) {
        if (map.containsKey(key)) {
            map.get(key).addAll(values);
        } else {
            map.put(key, new HashSet<>(values));
        }
    }

    /**
     * Get the set of all values of a setmap. Equivalent to {@code Multimap.values()}.
     *
     * @param map The map.
     * @param <V> The type of the map values.
     * @return A (flattened) set of all the values of {@code map}.
     */
    public static <V> Set<V> getSetMapValues(Map<?, HashSet<V>> map) {
        Set<V> values = new HashSet<>();
        map.values().forEach(values::addAll);
        return values;
    }
}
