package com.chillzone.npcs;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum NpcColor {
    WHITE("white", ChatFormatting.WHITE),
    GOLD("gold", ChatFormatting.GOLD),
    YELLOW("yellow", ChatFormatting.YELLOW),
    AQUA("aqua", ChatFormatting.AQUA),
    GREEN("green", ChatFormatting.GREEN),
    RED("red", ChatFormatting.RED),
    PURPLE("purple", ChatFormatting.LIGHT_PURPLE),
    GRAY("gray", ChatFormatting.GRAY),
    BLUE("blue", ChatFormatting.BLUE),
    DARK_AQUA("dark_aqua", ChatFormatting.DARK_AQUA),
    DARK_GREEN("dark_green", ChatFormatting.DARK_GREEN),
    DARK_RED("dark_red", ChatFormatting.DARK_RED);

    private final String id;
    private final ChatFormatting formatting;

    NpcColor(String id, ChatFormatting formatting) {
        this.id = id;
        this.formatting = formatting;
    }

    public String id() { return id; }
    public ChatFormatting formatting() { return formatting; }

    public static NpcColor fromId(String raw) {
        if (raw == null) return WHITE;
        String value = raw.toLowerCase(Locale.ROOT);
        for (NpcColor color : values()) if (color.id.equals(value)) return color;
        return WHITE;
    }
}
