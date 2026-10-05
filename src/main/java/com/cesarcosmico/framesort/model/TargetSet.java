package com.cesarcosmico.framesort.model;

import java.util.ArrayList;
import java.util.List;

/** Keeps only the targets tied at the best (lowest) priority seen so far. */
public final class TargetSet<T> {

    private final List<T> targets = new ArrayList<>();
    private int best = Integer.MAX_VALUE;

    public boolean isRelevant(int priority) {
        return priority <= best;
    }

    public void add(int priority, T target) {
        if (priority > best) {
            return;
        }
        if (priority < best) {
            targets.clear();
            best = priority;
        }
        targets.add(target);
    }

    public List<T> targets() {
        return List.copyOf(targets);
    }
}
