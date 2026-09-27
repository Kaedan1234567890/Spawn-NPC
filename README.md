# Chill Zone NPCs 0.2.1-alpha

Server-side Fabric NPC system for Minecraft 26.2.

## New in 0.2.1-alpha

- Player-skin command: `/npc skin <id> <player>`
  - Accepts any typed Java username; autocomplete includes online/remembered server players.
  - `/npc skin <id> clear` restores the default mannequin skin.
- Message styling without losing the message or command action:
  - `/npc action <id> messagecolor <color>`
  - `/npc action <id> messageformat <format>`
- NPC name formatting:
  - `/npc nameformat <id> <format>`
- Added `/npc tracking <id> on|off` as an easy alias for `/npc look <id> on|off`.
  - OFF freezes the NPC at its current horizontal facing direction.
  - ON resumes horizontal-only nearest-player tracking.
- Existing persistent NPC data from 0.2.0 is migrated with defaults.

## Colors
`white`, `gold`, `yellow`, `aqua`, `green`, `red`, `purple`, `gray`, `blue`, `dark_aqua`, `dark_green`, `dark_red`

## Text formats
`default`, `bold`, `italic`, `underline`, `bold_italic`, `bold_underline`

## Examples

```text
/npc skin shop_main DrDonut
/npc action shop_main message Welcome to the Shard Shop!
/npc action shop_main messagecolor gold
/npc action shop_main messageformat bold
/npc nameformat shop_main bold_underline
/npc tracking shop_main off
```

The mod intentionally uses Minecraft's normal font. True custom typefaces require client resource-pack assets and are not guaranteed to render the same way through Bedrock/Geyser; color and formatting remain server-side/crossplay-friendly.
