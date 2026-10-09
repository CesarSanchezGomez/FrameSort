package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.function.Supplier;

// Every tag action starts with a fixed word, so the client always suggests all of them and no argument hides another.
public final class TagCommand implements CommandFeature {

    private final TagListCommand list;
    private final TagShowCommand show;
    private final TagApplyCommand apply;

    public TagCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        TagArgument tag = new TagArgument(tags, messages);
        this.list = new TagListCommand(tags, messages, settings);
        this.show = new TagShowCommand(tag, messages, settings);
        this.apply = new TagApplyCommand(tag, messages, settings);
    }

    @Override
    public String id() {
        return "tag";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.then(list.list(path))
                .then(list.search(path))
                .then(show.show(path))
                .then(apply.apply())
                .then(apply.remove());
    }
}
