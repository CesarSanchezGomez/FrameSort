package com.cesarcosmico.framesort.model;

/** Who can create a teleport pad. */
public enum PadMode {
    /** Any player who completes the structure by placing its top block. */
    ANYONE,
    /** Only players with the pad creation permission. */
    PERMISSION,
    /** Only by placing the pad's special item as the top block. */
    ITEM
}
