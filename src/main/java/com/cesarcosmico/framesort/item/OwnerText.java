package com.cesarcosmico.framesort.item;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** An owner written as text in PDC, because Paper has no data type for a UUID. */
final class OwnerText {

    private OwnerText() {
    }

    static @Nullable UUID parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
