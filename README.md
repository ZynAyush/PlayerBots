# PlayerBots

PlayerBots is a server-side fake player plugin for Paper and Purpur.

It lets you spawn and control fake players that can interact with the world, hold items, attack, use items, sneak, collide with players and keep their bot data between sessions.

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

## Commands

### Bot Management

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
````

### Attack

```text
/bot attack <name>
/bot attack <name> single
/bot attack <name> continuously
/bot attack <name> interval <ticks>
/bot attack <name> stop
```

`single` is the default mode.

Examples:

```text
/bot attack BOT_Test
/bot attack BOT_Test single
/bot attack BOT_Test continuously
/bot attack BOT_Test interval 20
/bot attack BOT_Test stop
```

### Item Use

```text
/bot use <name>
/bot use <name> single
/bot use <name> continuously
/bot use <name> interval <ticks>
/bot use <name> stop
```

`single` is the default mode.

Examples:

```text
/bot use BOT_Test
/bot use BOT_Test single
/bot use BOT_Test continuously
/bot use BOT_Test interval 20
/bot use BOT_Test stop
```

### Original Bots

```text
/bot original spawn <name>
/bot original grant <player>
/bot original revoke <player>
```

Original bots are intended for administrative or permanent bot use.

## Inventory

Bot inventory can be preserved when inventory preservation is enabled.

Preservation can apply when using:

```text
/bot kill
/bot kill_other
```

and can also apply to normal bot deaths.

Saved bot inventory is restored when the bot is spawned again.

## Offhand

Bots have a normal main hand and offhand.

Move or swap an inventory slot with the offhand:

```text
/bot offhand <name> <0-35>
```

Clear the offhand:

```text
/bot offhand <name> clear
```

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

PlayerBots includes AuthMe support for fake players.

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

Grants normal bot controls.

### `bot.action`

Grants attack and item-use controls.

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

## Bot Ownership

Bots are player-owned.

The maximum number of bots a player can own can be configured in `config.yml`.

The owner timeout can also be configured. Original bots can be exempt from the owner timeout.

## Default Bot Name

When `/bot spawn` is used without a custom name, PlayerBots creates:

```text
BOT_<player-name>
```

Example:

```text
BOT_Steve
```

## Physics

Bots use server-side entity physics.

This includes:

* Gravity
* Falling
* Collision
* Knockback
* Explosion knockback
* Normal entity movement

PlayerBots avoids continuously forcing the bot's position or velocity.

## Skins

Bots can use a player's skin with:

```text
/bot skin <name> <player>
```

## Configuration

Configuration is stored in:

```text
plugins/PlayerBots/config.yml
```

Available configuration areas include:

* Bot limits
* Owner timeout
* Owner warnings
* Physics
* Attributes
* Knockback
* Hunger
* Inventory preservation
* Collision
* AuthMe
* Messages

## Installation

1. Download the PlayerBots build matching your Minecraft version.
2. Stop your server.
3. Put the JAR into the `plugins` folder.
4. Start the server.
5. Edit `plugins/PlayerBots/config.yml` if needed.

## Building

Each Minecraft version has its own Gradle project.

Enter the version folder and run:

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

```
https://github.com/ZynAyush/minecraft-playerbots/blob/main/LICENSE
```
