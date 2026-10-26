package com.cesarcosmico.framesort.model;

/** Server ticks, as the schedulers count time. */
public final class Ticks {

    public static final int PER_SECOND = 20;

    private Ticks() {
    }

    public static long ofSeconds(long seconds) {
        return seconds * PER_SECOND;
    }
}
