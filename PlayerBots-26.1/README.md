# PlayerBots — Minecraft 26.1

A server-side fake-player plugin for Paper and Purpur.

## Commands

```text
/bot spawn
/bot kill
/bot rejoin
/bot info <name>
/bot hotbar <name> <0-8>
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

## Requirements

- Minecraft 26.1.x
- Paper or Purpur 26.1.x
- Java 25

## Build

```bat
.\gradlew.bat clean build
```

The output JAR is written to `build/libs/`.

## Source

This folder is a standalone source project for Minecraft 26.1.x.
