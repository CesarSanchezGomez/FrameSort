package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {

    @Test
    void splitsIntoPagesOfTen() {
        Page page = Page.of(2, 25, 10);
        assertEquals(new Page(2, 3, 10, 20), page);
        assertTrue(page.hasPrevious());
        assertTrue(page.hasNext());
        assertEquals(new Page(3, 3, 20, 25), Page.of(3, 25, 10));
    }

    @Test
    void clampsRequestedPage() {
        assertEquals(new Page(3, 3, 20, 25), Page.of(99, 25, 10));
        assertEquals(new Page(1, 3, 0, 10), Page.of(-4, 25, 10));
    }

    @Test
    void emptyListHasOneEmptyPage() {
        Page page = Page.of(1, 0, 10);
        assertEquals(new Page(1, 1, 0, 0), page);
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void rejectsNonPositivePageSize() {
        assertThrows(IllegalArgumentException.class, () -> Page.of(1, 5, 0));
    }
}
