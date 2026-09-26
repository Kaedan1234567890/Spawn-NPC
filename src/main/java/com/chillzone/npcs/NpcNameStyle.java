package com.chillzone.npcs;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

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
    private final boolean bold;

    NpcNameStyle(String id, ChatFormatting color, boolean bold) {
        this.id = id;
        this.color = color;
        this.bold = bold;
    }

    public String id() { return id; }

    public Component format(String text) {
        return bold
                ? Component.literal(text).withStyle(color, ChatFormatting.BOLD)
                : Component.literal(text).withStyle(color);
    }

    public static NpcNameStyle fromId(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        for (NpcNameStyle style : values()) if (style.id.equals(value)) return style;
        return GOLD;
    }
}
