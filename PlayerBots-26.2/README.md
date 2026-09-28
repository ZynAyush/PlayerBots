# PlayerBots — Minecraft 26.2

A server-side fake-player plugin for Paper and Purpur.

## Commands

```text
/bot spawn
/bot kill
/bot rejoin
/bot info <name>
/bot hotbar <name> <0-8>
/bot offhand <name> <0-35>
/bot offhand <name> clear
/bot drop <name>
/bot dropstack <name>
/bot dropinv <name>
/bot sneak <name> on|off
/bot skin <name> <player>
/bot reload
/bot attack <name>
/bot attack <name> single
/bot attack <name> continuously
/bot attack <name> interval <ticks>
/bot attack <name> stop
/bot use <name>
/bot use <name> single
/bot use <name> continuously
/bot use <name> interval <ticks>
/bot use <name> stop
/bot original spawn <name>
/bot original grant <player>
/bot original revoke <player>
```

`single` is the default action mode.

## Hunger

Attack and item-use exhaustion can be controlled separately in `config.yml`:

```yml
hunger:
  attack: true
  use: true
```

## Inventory

Inventory preservation is configurable for bot deaths and kill commands.

## AuthMe

Includes fake-player AuthMe compatibility.

## LuckPerms

Broad permission groups are available through `bot.*`, `bot.player` and `bot.action`.

## Requirements

- Minecraft 26.2
- Paper or Purpur 26.2
- Java 25

## Build

```bat
.\gradlew.bat clean build
```

The output JAR is written to `build/libs/`.

## Source

This folder is a standalone source project for Minecraft 26.2.

## Development note

Join/leave chat messaging is included in the current source but should be tested with the target plugin stack before being treated as a guaranteed compatibility feature.
