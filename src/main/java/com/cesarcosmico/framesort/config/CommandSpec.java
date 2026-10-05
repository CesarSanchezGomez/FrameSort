package com.cesarcosmico.framesort.config;

import java.util.List;

/**
 * One command feature from {@code commands.yml}.
 *
 * @param paths every path that runs the feature, as words without the slash: {@code [framesort, tag]}
 */
public record CommandSpec(String id, boolean enabled, String permission, List<List<String>> paths) {

    public CommandSpec {
        paths = paths.stream().map(List::copyOf).toList();
    }
}
