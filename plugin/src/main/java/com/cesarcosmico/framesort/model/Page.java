package com.cesarcosmico.framesort.model;

/** One page of a list: 1-based {@code number}, and the {@code [from, to)} range of entries it shows. */
public record Page(int number, int count, int from, int to) {

    /** Clamps {@code requested} to the pages that exist; an empty list still has one (empty) page. */
    public static Page of(int requested, int size, int perPage) {
        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be positive");
        }
        int count = Math.max(1, (size + perPage - 1) / perPage);
        int number = Math.clamp(requested, 1, count);
        int from = Math.min(size, (number - 1) * perPage);
        return new Page(number, count, from, Math.min(size, from + perPage));
    }

    public boolean hasPrevious() {
        return number > 1;
    }

    public boolean hasNext() {
        return number < count;
    }
}
