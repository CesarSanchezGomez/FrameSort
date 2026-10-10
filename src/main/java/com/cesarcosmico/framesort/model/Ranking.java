package com.cesarcosmico.framesort.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Targets grouped by priority, best (lowest) first; ties keep the order they were added in. */
public final class Ranking<T> {

    private final Map<Integer, List<T>> levels = new TreeMap<>();

    public void add(int priority, T target) {
        levels.computeIfAbsent(priority, key -> new ArrayList<>()).add(target);
    }

    public List<List<T>> levels() {
        return levels.values().stream().map(List::copyOf).toList();
    }
}
