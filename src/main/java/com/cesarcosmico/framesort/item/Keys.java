package com.cesarcosmico.framesort.item;

import org.bukkit.NamespacedKey;

public final class Keys {

    private static final String NAMESPACE = "framesort";

    public static final NamespacedKey ACTIVATOR = new NamespacedKey(NAMESPACE, "activator");
    public static final NamespacedKey TARGET = new NamespacedKey(NAMESPACE, "target");
    public static final NamespacedKey PAD_ITEM = new NamespacedKey(NAMESPACE, "pad-item");
    public static final NamespacedKey PADS = new NamespacedKey(NAMESPACE, "pads");

    private Keys() {
    }
}
