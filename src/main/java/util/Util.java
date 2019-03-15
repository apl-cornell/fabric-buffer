package util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Util {
    public static <T, V> void addToSetMap(Map<T, HashSet<V>> map, T key, V value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            HashSet<V> s = new HashSet<>();
            s.add(value);
            map.put(key, s);
        }
    }

    public static <T, V> void addToSetMap(Map<T, HashSet<V>> map, T key, Collection<V> values) {
        if (map.containsKey(key)) {
            map.get(key).addAll(values);
        } else {
            map.put(key, new HashSet<>(values));
        }
    }

    public static <V> Set<V> getSetMapValues(Map<?, HashSet<V>> map) {
        Set<V> values = new HashSet<>();
        map.values().forEach(values::addAll);
        return values;
    }
}
