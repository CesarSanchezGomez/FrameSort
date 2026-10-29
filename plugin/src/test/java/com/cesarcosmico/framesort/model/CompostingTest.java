package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.PrimitiveIterator;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompostingTest {

    private static Composting.Fill fill(int level, float chance, int amount, double... rolls) {
        PrimitiveIterator.OfDouble next = DoubleStream.of(rolls).iterator();
        return Composting.fill(level, chance, amount, next::nextDouble);
    }

    @Test
    void anEmptyComposterAlwaysRises() {
        assertEquals(new Composting.Fill(1, 1), fill(0, 0.3f, 1));
    }

    @Test
    void risesOnlyWhenTheRollIsBelowTheChance() {
        assertEquals(new Composting.Fill(1, 2), fill(1, 0.5f, 1, 0.49));
        assertEquals(new Composting.Fill(1, 1), fill(1, 0.5f, 1, 0.5));
    }

    @Test
    void consumesEveryItemOfferedUntilFull() {
        assertEquals(new Composting.Fill(3, 2), fill(1, 0.5f, 3, 0.9, 0.1, 0.9));
    }

    @Test
    void stopsAtFull() {
        assertEquals(new Composting.Fill(7, Composting.FULL), fill(0, 1f, 10, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void aFullOrReadyComposterTakesNothing() {
        assertEquals(new Composting.Fill(0, 7), fill(7, 1f, 5));
        assertEquals(new Composting.Fill(0, 8), fill(8, 1f, 5));
    }
}
