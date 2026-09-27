# PlayerBots 1.0.0

Server-side fake players for Paper and Purpur 26.1.

## Build

Requires JDK 25 and an internet connection for the first Paperweight setup.

```text
./gradlew.bat clean build
```

Output:

`build/libs/PlayerBots-26.1-1.0.0.jar`

## Commands

```text
/bot spawn [name]
/bot kill [name|all]
/bot kill_other <name>
/bot rejoin [name]
/bot info <name>
/bot attack <name> [single|continuously|interval <ticks>|stop]
/bot use <name> [single|continuously|interval <ticks>|stop]
/bot hotbar <name> <0-8>
/bot drop <name>
/bot dropstack <name>
/bot dropinv <name>
/bot sneak <name> <on|off>
/bot skin <name> <player>
/bot reload
```

`single` is the default. Repeating actions run on the server tick loop.

## What it does

- Real `ServerPlayer` backed fake players
- Normal server-side gravity, collision and knockback
- Inventory persistence and rejoin snapshots
- AuthMe NPC marker bypass
- Player skins, sneaking, hand sync and item pickup
- Attack and use actions with single/interval/continuous modes
- LuckPerms support for original bots

## Compatibility

Built specifically for Minecraft/Paper 26.1.x. Do not mix this JAR with another PlayerBots version target.

The NMS code is kept under `com.example.bot.nms` so version-specific changes stay in one place.
## 26.1 compatibility fix

The 26.1 build uses the `Connection.send(Packet, ChannelFutureListener)` signature exposed by the 26.1 server mappings.

