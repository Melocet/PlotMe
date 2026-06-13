package com.worldcretornica.plotme_core.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Replacement for Guava's {@code Lists.partition}.
 * Splits a list into consecutive sublists of the given size; the last sublist
 * may be smaller. The returned list is unmodifiable and the sublists are
 * views backed by the input.
 */
public final class ListPartition {

    private ListPartition() {
    }

    public static <T> List<List<T>> partition(List<T> list, int size) {
        if (list == null) {
            throw new NullPointerException("list");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        int total = list.size();
        if (total == 0) {
            return Collections.emptyList();
        }
        int chunks = (total + size - 1) / size;
        List<List<T>> result = new ArrayList<>(chunks);
        for (int i = 0; i < total; i += size) {
            result.add(list.subList(i, Math.min(i + size, total)));
        }
        return Collections.unmodifiableList(result);
    }
}
