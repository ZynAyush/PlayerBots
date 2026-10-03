# PlayerBots 1.0.0

Server-side fake players for Paper and Purpur 26.3.

## Build

Requires JDK 25 and an internet connection for the first Paperweight setup.

```text
./gradlew.bat clean build
```

Output:

`build/libs/PlayerBots-26.3-1.0.0.jar`

## Commands

```text
/bot spawn [name]
/bot kill [name|all]
/bot kill_other <name>
/bot rejoin [name]
/bot info [name]
/bot attack [name] [single|continuously|interval <ticks>|stop]
/bot use [name] [single|continuously|interval <ticks>|stop]
/bot hotbar [name] <0-8>
/bot offhand [name] <0-35|clear>
/bot drop [name]
/bot dropstack [name] [item]
/bot dropinv [name]
/bot sneak [name] [on|off]
/bot skin [name] <player>
/bot mine [name] [single|continuously|interval <ticks>|stop]
/bot reload
```

`single` is the default. Repeating actions run on the server tick loop.

## Bot targeting

When a normal player owns exactly one active bot, bot-control commands automatically target that bot, so the bot name can be omitted. For example:

```text
/bot attack single
/bot hotbar 4
/bot offhand clear
/bot drop
/bot sneak
/bot skin <player>
```

When the player owns two or more active bots, the bot name is required so the command is unambiguous. Operators/admins still need to specify another player's bot by name. `/bot kill_other <name>` always requires an explicit name.

## What it does

- Real `ServerPlayer` backed fake players
- Normal server-side gravity, collision and knockback
- Inventory persistence and rejoin snapshots
- AuthMe NPC marker bypass
- Player skins, sneaking, hand sync and item pickup
- Attack and use actions with single/interval/continuous modes
- LuckPerms support for original bots

## Compatibility

Built specifically for Minecraft/Paper 26.3.x. Do not mix this JAR with another PlayerBots version target.

The NMS code is kept under `com.example.bot.nms` so version-specific changes stay in one place.

Build note: the fake connection uses Netty ChannelFutureListener, matching the 26.x Connection API for this target.

## Compatibility notes

PlayerBots keeps fake players out of normal vanilla player persistence and supplies a local connection address for plugins that expect a real player socket. Bot-owned quit cleanup also skips the known SunLight/NightCore NPC-unsafe quit handlers while leaving other quit integrations enabled.

Compatibility fix: RegisteredListener uses the Bukkit plugin package.

### Fake connection compatibility

The 26.3 build uses an in-memory Netty channel without opening a fake socket. The channel is marked as a PLAY connection and swallows synthetic channel exceptions so packet/protocol plugins do not produce repeated EmbeddedChannel errors for bots.


## Permissions

`bot.*` grants all PlayerBots permissions. `bot.player` grants normal player controls and `bot.action` grants action controls. Bot ownership is still enforced: only the owner, an OP, or an administrator with `bot.admin` can control a given bot.

To deny all PlayerBots permissions to the default group with LuckPerms:

```text
/lp group default permission set bot.* false
```

### Fake connection cleanup
The in-memory connection acknowledges close/disconnect requests without closing its transport. This avoids repeated `StacklessClosedChannelException` warnings during fake-player removal when packet plugins perform cleanup.

### Channel cleanup fix
The in-memory bot channel now ignores close/disconnect operations and is not finished during bot removal. This prevents packet/network compatibility plugins from repeatedly surfacing `StacklessClosedChannelException` during fake-player cleanup.


## Action hunger

Attack and use actions can be configured independently with `hunger.attack` and `hunger.use`. When disabled, the action restores the exhaustion value it had before the action, so normal food effects are still allowed.

## Offhand

Use `/bot offhand [name] <0-35>` to swap an inventory slot with the bot's offhand. `/bot offhand [name] clear` drops and clears the offhand. A lone owned bot can be targeted without `[name]`.


### LuckPerms prefix handling
Bots can optionally suppress inherited LuckPerms prefixes with `luckperms.hide-prefix: true`.

## Bot Ownership

Every spawned bot stores the UUID of its owner. A bot can only be controlled by:

- its owner;
- a server operator (OP); or
- an administrator with the `bot.admin` permission.

Having `bot.use`, `bot.player`, `bot.action`, `bot.mine`, or another normal command permission does **not** allow a player to control somebody else's bot. Knowing the bot's name is not enough.

`/bot kill_other <name>` and `/bot kill all` are operator/admin-only.

## Mining

`/bot mine` is an admin-only action. Normal players can mine through `/bot use` when the configured use fallback is enabled.

```text
/bot mine [name]
/bot mine [name] single
/bot mine [name] continuously
/bot mine [name] interval <ticks>
/bot mine [name] stop
```

Mining uses the server-side block break path and keeps the normal block protection, drops, durability and event checks.

## Sneak

Sneak is a toggle when no state is supplied:

```text
/bot sneak [name]
```

Run it again to toggle the state back off. `on` and `off` can still be supplied explicitly.

## LuckPerms Prefix

When enabled, PlayerBots applies a high-priority bot prefix so inherited group prefixes do not override the configured bot prefix.

## Access control

Normal players can only control bots they own. Administrative permissions are required for other-player bot management and `/bot mine`.

`/bot sneak [name]` is a toggle when no state is supplied: run it once to enable sneak and again to disable it.

`/bot use [name]` keeps normal use/interaction behavior and can fall back to real server-side block mining when `use.mine-fallback` is enabled.

Bots can use the configured custom PlayerBots prefix instead of an inherited LuckPerms group prefix.

## Rejoin behavior

 `/bot rejoin` restores your only available bot when you own one saved rejoin record. `/bot rejoin <name>` targets a specific saved bot. Rejoin recreates the bot like a normal spawn, then restores its saved inventory, held slot, world, XYZ position, and yaw/pitch. The saved location is re-applied one tick after login so join-handling plugins cannot leave the bot at their own teleport location.

