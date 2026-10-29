package com.cesarcosmico.framesort.model;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** {@code owner} is null for a pad saved before pads had owners. */
public record PadEntry(String type, @Nullable UUID owner) {
}
