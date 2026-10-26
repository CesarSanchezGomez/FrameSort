package com.cesarcosmico.framesort.item;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

// Paper has no persistent data type for a UUID.
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
