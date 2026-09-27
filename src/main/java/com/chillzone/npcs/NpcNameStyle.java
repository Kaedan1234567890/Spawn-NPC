package com.chillzone.npcs;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public enum NpcNameStyle {
    DEFAULT("default", ChatFormatting.WHITE, false),
    GOLD("gold", ChatFormatting.GOLD, true),
    YELLOW("yellow", ChatFormatting.YELLOW, true),
    AQUA("aqua", ChatFormatting.AQUA, true),
    GREEN("green", ChatFormatting.GREEN, true),
    RED("red", ChatFormatting.RED, true),
    PURPLE("purple", ChatFormatting.LIGHT_PURPLE, true),
    GRAY("gray", ChatFormatting.GRAY, true),
    WHITE("white", ChatFormatting.WHITE, true);

    private final String id;
    private final ChatFormatting color;
    private final boolean legacyBold;

    NpcNameStyle(String id, ChatFormatting color, boolean legacyBold) {
        this.id = id;
        this.color = color;
        this.legacyBold = legacyBold;
    }

    public String id() { return id; }

    public Component format(String text) {
        return format(text, null);
    }

    public Component format(String text, String formatId) {
        MutableComponent component = Component.literal(text).withStyle(color);
        if (formatId == null || formatId.isBlank()) {
            return legacyBold ? component.withStyle(ChatFormatting.BOLD) : component;
        }
        return NpcTextFormat.fromId(formatId).apply(component);
    }

    public static NpcNameStyle fromId(String raw) {
        if (raw == null) return GOLD;
        String value = raw.toLowerCase(Locale.ROOT);
        for (NpcNameStyle style : values()) if (style.id.equals(value)) return style;
        return GOLD;
    }
}
