package com.cesarcosmico.framesort.item;

import org.bukkit.NamespacedKey;

/** Every key FrameSort stores in persistent data containers. The namespace is the plugin name, lowercased. */
public final class Keys {

    private static final String NAMESPACE = "framesort";

    /** On an item: the sorter activator handed out by FrameSort. */
    public static final NamespacedKey ACTIVATOR = new NamespacedKey(NAMESPACE, "activator");
    /** On an item frame entity: the frame is a target (manual registration). */
    public static final NamespacedKey TARGET = new NamespacedKey(NAMESPACE, "target");
    /** On an item: the pad type of a special pad block. */
    public static final NamespacedKey PAD_ITEM = new NamespacedKey(NAMESPACE, "pad-item");
    /** On a chunk: the teleport pads registered in it. */
    public static final NamespacedKey PADS = new NamespacedKey(NAMESPACE, "pads");

    private Keys() {
    }
}
