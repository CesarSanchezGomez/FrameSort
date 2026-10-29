package com.cesarcosmico.framesort.model;

import java.util.function.DoubleSupplier;

/**
 * Vanilla's composter rule, applied one item at a time as a hopper does: {@code ComposterBlock} in Minecraft 26.2, and
 * the {@code compostable} item data in 26.3, whose vanilla values give the same result.
 */
public final class Composting {

    public static final int FULL = 7;

    public record Fill(int consumed, int level) {
    }

    private Composting() {
    }

    /** An empty composter always rises. {@code chance} is a compostable item's, so it is above 0. */
    public static Fill fill(int level, float chance, int amount, DoubleSupplier roll) {
        int consumed = 0;
        while (consumed < amount && level < FULL) {
            consumed++;
            if (level == 0 || roll.getAsDouble() < chance) {
                level++;
            }
        }
        return new Fill(consumed, level);
    }
}
