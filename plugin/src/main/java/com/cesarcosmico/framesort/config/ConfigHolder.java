package com.cesarcosmico.framesort.config;

import java.util.function.Supplier;

/**
 * A reloadable immutable value; build the replacement completely before {@link #set}, so readers never see half a
 * reload.
 */
public final class ConfigHolder<T> implements Supplier<T> {

    private volatile T value;

    public ConfigHolder(T initial) {
        this.value = initial;
    }

    @Override
    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
