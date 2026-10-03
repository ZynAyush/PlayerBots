# PlayerBots

PlayerBots is a server-side fake player plugin for Paper and Purpur.

It lets you spawn and control fake players that can interact with the world, hold items, attack, use items, sneak, collide with players, preserve inventory, and rejoin at their previous location.

## Supported Versions

| Minecraft | Server Software | Java |
|---|---|---|
| 26.1.x | Paper / Purpur | 25 |
| 26.2 | Paper / Purpur | 25 |
| 26.3 | Paper / Purpur | 25 |

Use the build that matches your Minecraft version.

## Features

- Fake player bots
- Player-owned bots
- Configurable bot limits
- Persistent bot data
- Persistent bot inventory
- Inventory restoration after bot deaths/removal
- Bot rejoining
- Rejoin at the bot's last saved location
- Saved world, position, yaw and pitch
- Combat and attack actions
- Item-use actions
- Single action mode
- Interval action mode
- Continuous action mode
- Main-hand support
- Offhand support
- Sneaking
- Entity collision
- Player skins
- Gravity
- Natural movement physics
- Natural knockback
- Explosion knockback
- Configurable hunger/exhaustion behavior
- AuthMe compatibility
- LuckPerms support
- Original/admin bots
- Configurable attributes
- Configurable messages
- Owner-based bot access control

## Commands

### Bot Management

Bot names are optional when the player owns **exactly one normal bot**.

For example:

```text
/bot spawn
/bot kill
/bot rejoin
/bot info
/bot hotbar 4
/bot offhand 12
/bot offhand clear
/bot drop
/bot dropstack
/bot dropinv
/bot sneak
/bot skin Steve
```

When the player owns multiple bots, the bot name must be specified:

```text
/bot info BOT_Test
/bot hotbar BOT_Test 4
/bot offhand BOT_Test 12
/bot offhand BOT_Test clear
/bot drop BOT_Test
/bot dropstack BOT_Test
/bot dropinv BOT_Test
/bot sneak BOT_Test
/bot skin BOT_Test Steve
```

This prevents ambiguity when a player has more than one bot.

Other management commands:

```text
/bot reload
```

### Attack

Attack commands support three modes:

```text
/bot attack
/bot attack single
/bot attack continuously
/bot attack interval <ticks>
/bot attack stop
```

When the player owns multiple bots:

```text
/bot attack <name>
/bot attack <name> single
/bot attack <name> continuously
/bot attack <name> interval <ticks>
/bot attack <name> stop
```

`single` is the default mode.

Example:

```text
/bot attack BOT_Test
/bot attack BOT_Test single
/bot attack BOT_Test continuously
/bot attack BOT_Test interval 20
/bot attack BOT_Test stop
```

### Item Use

Item-use commands support three modes:

```text
/bot use
/bot use single
/bot use continuously
/bot use interval <ticks>
/bot use stop
```

When the player owns multiple bots:

```text
/bot use <name>
/bot use <name> single
/bot use <name> continuously
/bot use <name> interval <ticks>
/bot use <name> stop
```

`single` is the default mode.

Example:

```text
/bot use BOT_Test
/bot use BOT_Test single
/bot use BOT_Test continuously
/bot use BOT_Test interval 20
/bot use BOT_Test stop
```

### Mining

Mining controls are restricted to administrators.

```text
/bot mine <name>
```

Depending on the configured command behavior, the bot can perform mining/use interactions through the server-side fake-player system.

### Other Bot Actions

```text
/bot drop
/bot dropstack
/bot dropinv
/bot hotbar <slot>
/bot offhand <slot>
/bot offhand clear
/bot sneak
/bot skin <player>
```

When the player owns multiple bots, specify the bot name before the action arguments.

## Bot Ownership and Access Control

Bots are player-owned.

A normal player can control **only bots they own**.

A bot can be controlled by:

- The bot's owner
- A server operator (OP)
- A PlayerBots administrator

PlayerBots administrators use:

```text
bot.admin
```

Having general PlayerBots permissions does **not** allow a player to control another player's bot.

For example, giving:

```text
bot.player
bot.action
bot.use
```

does not give a player permission to control somebody else's bot.

Ownership checks are performed directly by PlayerBots.

## Administrative Commands

Commands that affect other players' bots are restricted to operators or administrators.

Examples include:

```text
/bot kill_other <name>
/bot kill all
```

Administrative/original bot management:

```text
/bot original spawn <name>
/bot original grant <player>
/bot original revoke <player>
```

## Implicit Bot Targeting

PlayerBots supports automatic bot targeting.

If a player owns exactly **one normal bot**, the bot name can be omitted from supported commands.

Example:

```text
/bot attack
```

automatically targets the player's only bot.

Likewise:

```text
/bot use
/bot rejoin
/bot kill
/bot drop
/bot dropinv
/bot sneak
```

target that bot when there is only one.

If the player owns two or more bots, the name must be supplied.

Example:

```text
/bot attack BOT_A
/bot attack BOT_B
```

This prevents commands from affecting the wrong bot.

## Rejoining Bots

`/bot rejoin` recreates a bot that was removed or disconnected while restoring its saved bot data.

The bot is rejoined using the same spawning system as `/bot spawn`, but its last saved location is restored afterward.

The saved location includes:

```text
World
X
Y
Z
Yaw
Pitch
```

Example:

```text
/bot rejoin
```

for a player with one bot, or:

```text
/bot rejoin BOT_Test
```

when the player owns multiple bots.

The bot is restored at its last known location rather than at the owner's current location.

Saved inventory and other persistent bot data are restored as part of the rejoin process.

## Inventory

Bot inventory can be preserved when inventory preservation is enabled.

Preservation can apply when using:

```text
/bot kill
/bot kill_other <name>
```

and can also apply to normal bot deaths.

Saved bot inventory is restored when the bot is spawned or rejoined again.

## Offhand

Bots have a normal main hand and offhand.

Move an inventory slot into the offhand:

```text
/bot offhand <slot>
```

where `<slot>` is:

```text
0-35
```

Clear the offhand:

```text
/bot offhand clear
```

When multiple bots are owned:

```text
/bot offhand <name> <slot>
/bot offhand <name> clear
```

## Sneaking

Sneaking can be toggled for a bot:

```text
/bot sneak
```

or, with multiple owned bots:

```text
/bot sneak <name>
```

Each use toggles the bot's sneaking state.

## Hunger and Saturation

Attack and item-use exhaustion can be controlled separately.

Example:

```yml
hunger:
  attack: true
  use: true
```

Set either value to `false` to disable exhaustion caused by that action.

## AuthMe

PlayerBots includes AuthMe compatibility for fake players.

Fake-player connections are handled specially so authentication-related plugins can coexist with PlayerBots.

## LuckPerms

PlayerBots supports LuckPerms.

Broad permission groups are available:

```text
bot.*
bot.player
bot.action
```

### `bot.*`

Grants all PlayerBots permissions.

### `bot.player`

Grants normal bot-control permissions.

### `bot.action`

Grants attack and item-use permissions.

Individual permissions are also available:

```text
bot.admin
bot.kill-other
bot.original
bot.original.grant
bot.reload
bot.rejoin
bot.use
```

Example:

```text
/lp group default permission set bot.* false
```

Grant access again:

```text
/lp group default permission set bot.* true
```

Permission nodes do not override bot ownership restrictions for normal players.

## Original Bots

Original bots are intended for administrative or permanent bot use.

Commands:

```text
/bot original spawn <name>
/bot original grant <player>
/bot original revoke <player>
```

Original bots can have different persistence/ownership behavior from normal player-owned bots depending on configuration.

## Default Bot Name

When `/bot spawn` is used without a custom name, PlayerBots creates:

```text
BOT_<player-name>
```

Example:

```text
BOT_Steve
```

## Bot Limits

The maximum number of normal bots a player can own can be configured in:

```text
plugins/PlayerBots/config.yml
```

The configured limit prevents players from creating unlimited bots.

## Owner Timeout

Player-owned bots can be automatically removed when their owner leaves the server.

The owner timeout is configurable in `config.yml`.

Original bots can be configured to be exempt from the owner timeout.

## Physics

Bots use server-side entity physics.

This includes:

- Gravity
- Falling
- Collision
- Knockback
- Explosion knockback
- Normal entity movement

PlayerBots avoids continuously forcing the bot's position or velocity so normal server physics can operate.

## Skins

Bots can use another player's skin.

For a single owned bot:

```text
/bot skin Steve
```

For multiple owned bots:

```text
/bot skin BOT_Test Steve
```

## Configuration

Configuration is stored in:

```text
plugins/PlayerBots/config.yml
```

Available configuration areas include:

- Bot limits
- Owner timeout
- Owner warnings
- Physics
- Attributes
- Knockback
- Hunger
- Inventory preservation
- Collision
- AuthMe
- Messages

## Installation

1. Download the PlayerBots build matching your Minecraft version.
2. Stop your server.
3. Put the JAR into the `plugins` folder.
4. Start the server.
5. Edit `plugins/PlayerBots/config.yml` if needed.

Use only the build matching your Minecraft version.

## Building

Each Minecraft version has its own Gradle project.

Enter the desired version folder and run:

```bat
.\gradlew.bat clean build
```

Example:

```bat
cd 26.2
.\gradlew.bat clean build
```

The compiled JAR will be placed in:

```text
build/libs/
```

Build each Minecraft version separately.

## Source Structure

```text
PlayerBots/
├── 26.1/
│   ├── build.gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── settings.gradle
│   ├── README.md
│   └── src/
│
├── 26.2/
│   ├── build.gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── settings.gradle
│   ├── README.md
│   └── src/
│
├── 26.3/
│   ├── build.gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── settings.gradle
│   ├── README.md
│   └── src/
│
├── LICENSE
└── README.md
```

## Reporting Issues

When reporting an issue, include:

```text
Minecraft version:
Server software and version:
Java version:
PlayerBots version:
Relevant plugins:
Full console error:
Steps to reproduce:
```

Please include the complete stack trace whenever possible.

## License

PlayerBots is released under the MIT License.

See [LICENSE](https://github.com/ZynAyush/minecraft-playerbots/blob/main/LICENSE) for the full license text.
