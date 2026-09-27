package com.chillzone.npcs;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public enum NpcTextFormat {
    DEFAULT("default"),
    BOLD("bold", ChatFormatting.BOLD),
    ITALIC("italic", ChatFormatting.ITALIC),
    UNDERLINE("underline", ChatFormatting.UNDERLINE),
    BOLD_ITALIC("bold_italic", ChatFormatting.BOLD, ChatFormatting.ITALIC),
    BOLD_UNDERLINE("bold_underline", ChatFormatting.BOLD, ChatFormatting.UNDERLINE);

    private final String id;
    private final ChatFormatting[] formatting;

    NpcTextFormat(String id, ChatFormatting... formatting) {
        this.id = id;
        this.formatting = formatting;
    }

    public String id() { return id; }

    public MutableComponent apply(MutableComponent component) {
        return formatting.length == 0 ? component : component.withStyle(formatting);
    }

    public static NpcTextFormat fromId(String raw) {
        if (raw == null) return DEFAULT;
        String value = raw.toLowerCase(Locale.ROOT);
        for (NpcTextFormat format : values()) if (format.id.equals(value)) return format;
        return DEFAULT;
    }
}
