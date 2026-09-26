# Chill Zone NPCs 0.2.0-alpha

Server-side Fabric 26.2 NPC system for the Chill Zone SMP.

## What changed from 0.1.1

- Removed the preset NPC system. Every NPC is now custom.
- `/npc create <id>` creates a custom NPC using the ID you choose.
- NPCs can now have BOTH a command action and a message action at the same time.
- Added `/npc style <id> <style>` for cleaner coloured/bold nameplates.
- Hides the vanilla mannequin `NPC` description line so only the Chill Zone display name shows.
- Added duplicate prevention for leave/rejoin and normal server restarts:
  - restoration waits briefly for persisted entities to load;
  - an existing matching mannequin at the stored position is adopted instead of duplicated;
  - duplicate copies found at the same stored position are removed;
  - live NPC entities are discarded on clean shutdown while the JSON records remain authoritative.
- Existing 0.1.x NPC data migrates automatically, including old single command/message actions.

## Commands

All `/npc` administration remains OP-only on a dedicated server. Single-player creative testing remains supported through the existing permission helper.

```text
/npc create <id>
/npc name <id> <display name>
/npc style <id> <default|gold|yellow|aqua|green|red|purple|gray|white>

/npc action <id> command <command>
/npc action <id> message <message>
/npc action <id> clear command
/npc action <id> clear message
/npc action <id> clear all

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

### Example

```text
/npc create shop_main
/npc name shop_main Shard Shop
/npc style shop_main gold
/npc action shop_main command shop
/npc action shop_main message Welcome to the Shard Shop!
```

Right-clicking that NPC runs `/shop` as the clicking player and also sends that player the custom message.

## Appearance note

This build improves nameplate presentation and removes the vanilla `NPC` line. It does not yet attempt per-viewer dynamic skins. A single vanilla mannequin normally has one profile that every viewer receives. Making one Java player see their own skin while another viewer simultaneously sees a different skin requires per-viewer packet/entity handling and is not something to fake into the server-side crossplay build without testing it carefully with Geyser/Bedrock.

A safer future skin feature is a configurable fixed player skin per NPC (for example `/npc skin shop_main SomePlayer`) if desired.
