# Chill Zone NPCs 0.1.1-alpha

Persistent server-side mannequin NPCs for Minecraft Java 26.2 / Fabric.

## Goals in this first build
- `/npc` administration is OP-only on dedicated servers.
- Single-player/integrated worlds allow the world owner to test commands.
- NPCs use vanilla mannequins: no client mod or custom packets.
- NPC definitions persist in `config/chillzone-npcs/npcs.json` and automatically restore after restart.
- NPCs are invulnerable, gravity-free, attack-blocked, and position-locked.
- Optional horizontal-only look tracking follows the nearest player without looking up/down.
- Right-click actions can run a player command or send a custom message.
- Presets: `shop`, `homes`, `rtp`, `baltop`, `help`, `custom`.
- Command action entry uses the server command dispatcher for tab suggestions and also remembers offline player names seen by the server.

## Commands
```
/npc create <shop|homes|rtp|baltop|help|custom> <id>
/npc name <id> <display name>
/npc action <id> command <command>
/npc action <id> message <message>
/npc action <id> clear
/npc enable <id>
/npc disable <id>
/npc look <id> on
/npc look <id> off
/npc move <id>
/npc move <id> <x> <y> <z>
/npc remove <id>
/npc refresh <id>
/npc info <id>
/npc list
```

## Example
```
/npc create shop shop_main
/npc name shop_main Shard Shop
/npc look shop_main on
```

`shop` already has the action `shop`. A custom NPC can be configured like:
```
/npc create custom info_1
/npc name info_1 Server Information
/npc action info_1 message Welcome to Chill Zone SMP!
```

Command actions execute as the player who right-clicked the NPC, so normal command permissions still apply. This is intentional: a custom NPC does not silently give regular players OP permissions. Selectors such as `@p` can still be entered in the stored command where the command itself permits them.

## Crossplay
The mod is entirely server-side and uses a vanilla mannequin plus vanilla interaction/command handling. Java clients do not need the mod. Bedrock clients are expected to use the server's normal Geyser/Floodgate bridge; there is no Bedrock-specific client install for this mod.


## 0.1.1-alpha build fix
- Fixed Minecraft 26.2 ServerPlayer server access by using `player.level().getServer()` when executing NPC command actions.
