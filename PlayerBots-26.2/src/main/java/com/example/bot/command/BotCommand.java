package com.example.bot.command;

import com.example.bot.BotPlugin;
import com.example.bot.bot.Bot;
import com.example.bot.bot.BotInventory;
import com.example.bot.bot.BotAttack;
import com.example.bot.bot.BotManager;
import com.example.bot.bot.BotUse;
import com.example.bot.config.BotConfig;
import com.example.bot.nms.FakePlayerController;
import com.example.bot.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BotCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "spawn", "original", "kill", "kill_other", "rejoin", "info", "attack", "use", "hotbar", "offhand", "drop", "dropstack", "dropinv", "sneak", "skin", "reload"
    );

    private final BotManager manager;
    private final BotConfig config;

    public BotCommand(BotManager manager, BotConfig config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!config.isEnabled()) {
            MessageUtil.error(sender, "The Bot plugin is disabled in config.yml.");
            return true;
        }
        if (!sender.hasPermission("bot.use") && !sender.hasPermission("bot.admin")) {
            MessageUtil.error(sender, "You do not have permission to use /bot.");
            return true;
        }
        if (args.length == 0) {
            MessageUtil.info(sender, "Usage: /bot <spawn|original|kill|kill_other|rejoin|info|attack|use|hotbar|offhand|drop|dropstack|dropinv|sneak|skin|reload> ...");
            MessageUtil.info(sender, "Tip: /bot spawn uses BOT_<your-name> automatically when no name is supplied.");
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "spawn" -> handleSpawn(sender, args);
                case "original" -> handleOriginal(sender, args);
                case "kill" -> handleKill(sender, args);
                case "kill_other" -> handleKillOther(sender, args);
                case "rejoin" -> handleRejoin(sender, args);
                case "info" -> handleInfo(sender, args);
                case "attack" -> handleAttack(sender, args);
                case "use" -> handleUse(sender, args);
                case "hotbar" -> handleHotbar(sender, args);
                case "offhand" -> handleOffhand(sender, args);
                case "drop" -> handleDrop(sender, args);
                case "dropstack" -> handleDropStack(sender, args);
                case "dropinv" -> handleDropInv(sender, args);
                case "sneak" -> handleSneak(sender, args);
                case "skin" -> handleSkin(sender, args);
                case "reload" -> handleReload(sender, args);
                default -> MessageUtil.error(sender, "Unknown subcommand: " + args[0]);
            }
        } catch (NumberFormatException ex) {
            MessageUtil.error(sender, "Invalid number: " + ex.getMessage());
        } catch (Exception ex) {
            MessageUtil.error(sender, "Command failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
        return true;
    }

    private void handleOriginal(CommandSender sender, String[] args) {
        if (args.length >= 2 && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("revoke"))) {
            handleOriginalPermission(sender, args);
            return;
        }
        if (!sender.hasPermission("bot.original")) {
            MessageUtil.error(sender, "You do not have permission to use /bot original spawn.");
            return;
        }
        if (args.length != 3 || !args[1].equalsIgnoreCase("spawn")) {
            MessageUtil.error(sender, "Usage: /bot original spawn <name> | /bot original grant <player> | /bot original revoke <player>");
            return;
        }
        String name = args[2];
        Location location;
        if (sender instanceof Player player) {
            location = player.getLocation();
        } else {
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) {
                MessageUtil.error(sender, "No loaded world is available for console spawning.");
                return;
            }
            location = world.getSpawnLocation();
        }
        UUID ownerUuid = sender instanceof Player player ? player.getUniqueId() : null;
        BotManager.SpawnResult result = manager.spawn(name, location, true, null, ownerUuid, true);
        switch (result) {
            case OK -> MessageUtil.success(sender, "Spawned original bot " + name + " at " + fmt(location) + ".");
            case DUPLICATE_NAME -> MessageUtil.error(sender, "A bot named " + name + " already exists.");
            case LIMIT_REACHED -> MessageUtil.error(sender, "Server bot limit reached.");
            case INVALID_NAME -> MessageUtil.error(sender, "Invalid bot name. Use 1-16 characters.");
        }
    }

    private void handleOriginalPermission(CommandSender sender, String[] args) {
        if (args.length != 3) {
            MessageUtil.error(sender, "Usage: /bot original grant <player> | /bot original revoke <player>");
            return;
        }
        boolean privileged = sender.hasPermission("bot.admin") || sender.hasPermission("bot.original.grant");
        if (!privileged && sender instanceof Player player) {
            privileged = manager.hasOwnedOriginal(player.getUniqueId());
        }
        if (!privileged) {
            MessageUtil.error(sender, "Only an original-bot owner or an operator can grant/revoke bot.original.");
            return;
        }
        String target = args[2];
        if (!target.matches("[A-Za-z0-9_]{1,16}")) {
            MessageUtil.error(sender, "Invalid Minecraft player name.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            MessageUtil.error(sender, "LuckPerms is not installed; /bot original grant/revoke requires LuckPerms.");
            return;
        }
        boolean grant = args[1].equalsIgnoreCase("grant");
        String lpCommand = grant
                ? "lp user " + target + " permission set bot.original true"
                : "lp user " + target + " permission unset bot.original";
        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lpCommand);
        if (success) {
            MessageUtil.success(sender, (grant ? "Granted" : "Revoked") + " bot.original for " + target + ".");
        } else {
            MessageUtil.error(sender, "LuckPerms rejected the permission command. Check the server console.");
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        String name;
        int cursor;

        if (args.length < 2) {
            name = defaultBotName(sender);
            cursor = 1;
        } else if (args[1].equalsIgnoreCase("at") || args[1].equalsIgnoreCase("persistent")) {
            name = defaultBotName(sender);
            cursor = 1;
        } else {
            name = args[1];
            cursor = 2;
        }

        boolean persistent = config.isPersistentByDefault() || contains(args, "persistent");
        Location location;

        if (cursor >= args.length || args[cursor].equalsIgnoreCase("persistent")) {
            if (!(sender instanceof Player p)) {
                World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (world == null) {
                    MessageUtil.error(sender, "No loaded world is available for console spawning.");
                    return;
                }
                location = world.getSpawnLocation();
            } else {
                location = p.getLocation();
            }
        } else if (args[cursor].equalsIgnoreCase("at")) {
            if (cursor + 3 >= args.length) {
                MessageUtil.error(sender, "Usage: /bot spawn [name] at <x> <y> <z> [in <world>] [facing <yaw> <pitch>] [persistent]");
                return;
            }
            double x = Double.parseDouble(args[cursor + 1]);
            double y = Double.parseDouble(args[cursor + 2]);
            double z = Double.parseDouble(args[cursor + 3]);
            World world = sender instanceof Player p ? p.getWorld() : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
            int i = cursor + 4;
            float yaw = 0f;
            float pitch = 0f;

            while (i < args.length) {
                String option = args[i].toLowerCase(Locale.ROOT);
                if (option.equals("in")) {
                    if (++i >= args.length) {
                        MessageUtil.error(sender, "Missing world after 'in'.");
                        return;
                    }
                    world = Bukkit.getWorld(args[i++]);
                } else if (option.equals("facing")) {
                    if (i + 2 >= args.length) {
                        MessageUtil.error(sender, "Usage: ... facing <yaw> <pitch>");
                        return;
                    }
                    yaw = Float.parseFloat(args[i + 1]);
                    pitch = Float.parseFloat(args[i + 2]);
                    i += 3;
                } else if (option.equals("persistent")) {
                    persistent = true;
                    i++;
                } else {
                    MessageUtil.error(sender, "Unknown spawn option: " + args[i]);
                    return;
                }
            }

            if (world == null) {
                MessageUtil.error(sender, "Unknown or unavailable world.");
                return;
            }
            location = new Location(world, x, y, z, yaw, pitch);
        } else {
            MessageUtil.error(sender, "Usage: /bot spawn [name] [at <x> <y> <z> [in <world>] [facing <yaw> <pitch>]] [persistent]");
            return;
        }

        UUID ownerUuid = sender instanceof Player p ? p.getUniqueId() : null;
        BotManager.SpawnResult result = manager.spawn(name, location, persistent, null, ownerUuid);
        switch (result) {
            case OK -> MessageUtil.success(sender, "Spawned " + name + " at " + fmt(location) + ".");
            case DUPLICATE_NAME -> MessageUtil.error(sender, "A bot named " + name + " already exists.");
            case LIMIT_REACHED -> MessageUtil.error(sender, "Bot limit reached.");
            case INVALID_NAME -> MessageUtil.error(sender, "Invalid bot name. Use 1-16 characters.");
        }
    }

    private String defaultBotName(CommandSender sender) {
        String base;
        if (sender instanceof Player p) {
            base = config.getSpawnNamePrefix() + p.getName();
        } else {
            base = config.getSpawnNamePrefix() + "Console";
        }

        base = base.length() <= 16 ? base : base.substring(0, 16);
        if (manager.get(base) == null) return base;

        for (int i = 2; i <= 999; i++) {
            String suffix = String.valueOf(i);
            int prefixLen = Math.max(1, 16 - suffix.length());
            String candidate = base.substring(0, Math.min(base.length(), prefixLen)) + suffix;
            if (manager.get(candidate) == null) return candidate;
        }
        return null;
    }

    private void handleKill(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player player)) {
                MessageUtil.error(sender, "Console must specify a bot name or use /bot kill all.");
                return;
            }
            int count = manager.countByOwner(player.getUniqueId());
            if (count == 0) {
                MessageUtil.error(sender, "You have no bots to kill.");
                return;
            }
            if (count > 1) {
                MessageUtil.error(sender, "You have " + count + " bots. Use /bot kill <name>.");
                return;
            }
            Bot only = manager.getOnlyOwnedBot(player.getUniqueId());
            if (only != null && manager.kill(only.getName())) MessageUtil.success(sender, "Removed " + only.getName() + ".");
            else MessageUtil.error(sender, "Your bot could not be removed.");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            if (!sender.hasPermission("bot.admin")) {
                MessageUtil.error(sender, "Only admins can use /bot kill all.");
                return;
            }
            MessageUtil.success(sender, "Removed " + manager.killAll() + " bot(s).");
            return;
        }
        Bot bot = manager.get(args[1]);
        if (bot == null) {
            MessageUtil.error(sender, "No such bot: " + args[1]);
            return;
        }
        if (sender instanceof Player player
                && !sender.hasPermission("bot.admin")
                && !manager.isOwnedBy(bot, player.getUniqueId())) {
            MessageUtil.error(sender, "That is not your bot. Use /bot kill_other " + bot.getName() + ".");
            return;
        }
        if (manager.kill(bot.getName())) MessageUtil.success(sender, "Removed " + bot.getName() + ".");
        else MessageUtil.error(sender, "Could not remove " + bot.getName() + ".");
    }

    private void handleKillOther(CommandSender sender, String[] args) {
        if (args.length != 2) {
            MessageUtil.error(sender, "Usage: /bot kill_other <name>");
            return;
        }
        if (!sender.hasPermission("bot.kill-other")) {
            MessageUtil.error(sender, "You do not have permission to kill another player's bot.");
            return;
        }
        UUID requester = sender instanceof Player player ? player.getUniqueId() : null;
        Bot bot = manager.get(args[1]);
        if (bot == null) {
            MessageUtil.error(sender, "No such bot: " + args[1]);
            return;
        }
        if (requester != null && manager.isOwnedBy(bot, requester)) {
            MessageUtil.error(sender, "That is your bot. Use /bot kill " + bot.getName() + ".");
            return;
        }
        if (manager.killOther(bot.getName(), requester)) MessageUtil.success(sender, "Removed " + bot.getName() + ".");
        else MessageUtil.error(sender, "Could not remove " + bot.getName() + ".");
    }

    private void handleRejoin(CommandSender sender, String[] args) {
        if (!config.isRejoinEnabled()) {
            MessageUtil.error(sender, "Bot rejoin is disabled in config.yml.");
            return;
        }
        if (!sender.hasPermission("bot.rejoin") && !sender.hasPermission("bot.admin")) {
            MessageUtil.error(sender, "You do not have permission to rejoin bots.");
            return;
        }

        UUID requester = sender instanceof Player player ? player.getUniqueId() : null;
        String name;
        if (args.length == 1) {
            if (requester == null) {
                MessageUtil.error(sender, "Console must specify a bot name: /bot rejoin <name>");
                return;
            }
            var only = manager.getOnlyOwnedRejoin(requester);
            if (only == null) {
                if (manager.rejoinNames(requester).isEmpty()) {
                    MessageUtil.error(sender, "You have no bot available to rejoin.");
                } else {
                    MessageUtil.error(sender, "You have multiple bots available. Use /bot rejoin <name>.");
                }
                return;
            }
            name = only.name;
        } else if (args.length == 2) {
            name = args[1];
        } else {
            MessageUtil.error(sender, "Usage: /bot rejoin [name]");
            return;
        }

        var saved = manager.getRejoinRecord(name);
        if (saved == null) {
            MessageUtil.error(sender, "No rejoin record exists for " + name + ".");
            return;
        }
        if (!(sender.hasPermission("bot.admin") || requester != null && manager.isRejoinOwnedBy(saved, requester))) {
            MessageUtil.error(sender, "That is not your bot. Only its owner or an operator can rejoin it.");
            return;
        }

        BotManager.RejoinResult result = manager.rejoin(name, requester);
        switch (result) {
            case OK -> MessageUtil.success(sender, "Rejoined " + name + ".");
            case NOT_FOUND -> MessageUtil.error(sender, "No rejoin record exists for " + name + ".");
            case ACTIVE_NAME -> MessageUtil.error(sender, "A bot named " + name + " is already active.");
            case LIMIT_REACHED -> MessageUtil.error(sender, "Bot limit reached.");
            case WORLD_UNAVAILABLE -> MessageUtil.error(sender, "The saved world for " + name + " is not currently loaded.");
            case ORIGINAL_DISABLED -> MessageUtil.error(sender, "Original-bot rejoin is disabled in config.yml.");
            case FAILED -> MessageUtil.error(sender, "Could not rejoin " + name + ". Check the console for details.");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        Player p = bot.getEntity();
        Location l = p.getLocation();
        MessageUtil.info(sender, "Bot: " + bot.getName());
        MessageUtil.info(sender, "UUID: " + bot.getUuid());
        MessageUtil.info(sender, "World: " + l.getWorld().getName());
        MessageUtil.info(sender, "Position: " + round(l.getX()) + ", " + round(l.getY()) + ", " + round(l.getZ()));
        MessageUtil.info(sender, "Yaw/Pitch: " + round(l.getYaw()) + " / " + round(l.getPitch()));
        MessageUtil.info(sender, "Health: " + round(p.getHealth()) + "/" + round(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        MessageUtil.info(sender, "Action: " + bot.getCurrentAction());
        MessageUtil.info(sender, "Held slot: " + p.getInventory().getHeldItemSlot());
        MessageUtil.info(sender, "Held: " + p.getInventory().getItemInMainHand().getType());
        MessageUtil.info(sender, "Sneaking: " + bot.isSneaking());
        MessageUtil.info(sender, "Persistent: " + bot.isPersistent());
    }

    private void handleAttack(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;

        if (args.length == 2 || args[2].equalsIgnoreCase("single")) {
            if (args.length > 3) {
                MessageUtil.error(sender, "Usage: /bot attack <name> [single|continuously|interval <ticks>|stop]");
                return;
            }
            // A bot has one active input action at a time. Release a held USE
            // action before performing the explicit one-shot attack.
            BotUse.stop(bot);
            bot.clearAction();
            if (BotAttack.attackOnce(bot, config.getAttackReach(), config)) {
                MessageUtil.success(sender, bot.getName() + " attacked.");
            } else {
                MessageUtil.error(sender, bot.getName() + " has no valid target in reach.");
            }
            return;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("stop")) {
            BotUse.stop(bot);
            bot.clearAction();
            MessageUtil.success(sender, bot.getName() + " stopped attacking.");
            return;
        }

        // Accept the normal spelling plus common shorthand/typo variants so
        // the command remains forgiving without changing tab completion.
        if (isContinuousMode(mode)) {
            BotUse.stop(bot);
            bot.startRepeatingAction(com.example.bot.bot.BotAction.ATTACK,
                    com.example.bot.bot.BotActionMode.CONTINUOUS, 1);
            MessageUtil.success(sender, bot.getName() + " will attack continuously.");
            return;
        }

        if (mode.equals("interval")) {
            if (args.length != 4) {
                MessageUtil.error(sender, "Usage: /bot attack <name> interval <ticks>");
                return;
            }
            int ticks = Integer.parseInt(args[3]);
            if (ticks < 1) throw new NumberFormatException("interval must be at least 1 tick");
            BotUse.stop(bot);
            bot.startRepeatingAction(com.example.bot.bot.BotAction.ATTACK,
                    com.example.bot.bot.BotActionMode.INTERVAL, ticks);
            MessageUtil.success(sender, bot.getName() + " will attack every " + ticks + " tick(s).");
            return;
        }
        MessageUtil.error(sender, "Usage: /bot attack <name> [single|continuously|interval <ticks>|stop]");
    }

    private void handleUse(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;

        if (args.length == 2 || args[2].equalsIgnoreCase("single")) {
            if (args.length > 3) {
                MessageUtil.error(sender, "Usage: /bot use <name> [single|continuously|interval <ticks>|stop]");
                return;
            }
            // Explicit single mode cancels any previous repeating action so a
            // lingering ATTACK/USE loop cannot continue in the background.
            BotUse.stop(bot);
            bot.clearAction();
            if (BotUse.useOnce(bot, config)) MessageUtil.success(sender, bot.getName() + " used its held item.");
            else MessageUtil.error(sender, bot.getName() + " could not use its held item here.");
            return;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("stop")) {
            // releaseUsingItem() matters for foods, bows, crossbows and other
            // items which remain in a held-use state after the input starts.
            BotUse.stop(bot);
            bot.clearAction();
            MessageUtil.success(sender, bot.getName() + " stopped using its held item.");
            return;
        }

        if (isContinuousMode(mode)) {
            BotUse.stop(bot);
            bot.startRepeatingAction(com.example.bot.bot.BotAction.USE,
                    com.example.bot.bot.BotActionMode.CONTINUOUS, 1);
            MessageUtil.success(sender, bot.getName() + " will use its held item continuously.");
            return;
        }

        if (mode.equals("interval")) {
            if (args.length != 4) {
                MessageUtil.error(sender, "Usage: /bot use <name> interval <ticks>");
                return;
            }
            int ticks = Integer.parseInt(args[3]);
            if (ticks < 1) throw new NumberFormatException("interval must be at least 1 tick");
            BotUse.stop(bot);
            bot.startRepeatingAction(com.example.bot.bot.BotAction.USE,
                    com.example.bot.bot.BotActionMode.INTERVAL, ticks);
            MessageUtil.success(sender, bot.getName() + " will use its held item every " + ticks + " tick(s).");
            return;
        }
        MessageUtil.error(sender, "Usage: /bot use <name> [single|continuously|interval <ticks>|stop]");
    }

    private static boolean isContinuousMode(String mode) {
        return mode.equals("continuously")
                || mode.equals("continuous")
                || mode.equals("continously")
                || mode.equals("continous");
    }

    private void handleHotbar(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        if (args.length < 3) {
            MessageUtil.error(sender, "Usage: /bot hotbar <name> <0-8>");
            return;
        }
        int slot = Integer.parseInt(args[2]);
        if (!BotInventory.holdSlot(bot, slot, config)) {
            MessageUtil.error(sender, "Hotbar slot must be 0-8.");
            return;
        }
        MessageUtil.success(sender, bot.getName() + " selected hotbar slot " + slot + ".");
    }

    private void handleOffhand(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        if (args.length < 3) {
            MessageUtil.error(sender, "Usage: /bot offhand <name> <0-35|clear>");
            return;
        }
        String value = args[2].toLowerCase(Locale.ROOT);
        if (value.equals("clear")) {
            if (!BotInventory.clearOffhand(bot, config)) {
                MessageUtil.error(sender, "Could not clear " + bot.getName() + "'s offhand.");
                return;
            }
            MessageUtil.success(sender, bot.getName() + "'s offhand was cleared.");
            return;
        }
        int slot = Integer.parseInt(args[2]);
        if (!BotInventory.swapWithOffhand(bot, slot, config)) {
            MessageUtil.error(sender, "Offhand source slot must be 0-35.");
            return;
        }
        MessageUtil.success(sender, bot.getName() + " swapped inventory slot " + slot + " with its offhand.");
    }

    private void handleDrop(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        if (!BotInventory.dropHeld(bot, false, config)) {
            MessageUtil.error(sender, bot.getName() + " is not holding anything.");
            return;
        }
        MessageUtil.success(sender, bot.getName() + " dropped one item.");
    }

    private void handleDropStack(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        if (args.length >= 3) {
            Material material = parseMaterial(args[2]);
            if (material == null) {
                MessageUtil.error(sender, "Unknown item: " + args[2]);
                return;
            }
            if (!BotInventory.dropStackOf(bot, material, config)) {
                MessageUtil.error(sender, bot.getName() + " has no " + material + " stack.");
                return;
            }
        } else if (!BotInventory.dropHeld(bot, true, config)) {
            MessageUtil.error(sender, bot.getName() + " is not holding anything.");
            return;
        }
        MessageUtil.success(sender, bot.getName() + " dropped a full stack.");
    }

    private void handleDropInv(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        BotInventory.dropAll(bot, config);
        MessageUtil.success(sender, bot.getName() + " dropped its inventory.");
    }


    private void handleSneak(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        if (args.length < 3) {
            MessageUtil.error(sender, "Usage: /bot sneak <name> <on|off>");
            return;
        }
        String value = args[2].toLowerCase(Locale.ROOT);
        if (!value.equals("on") && !value.equals("off")) {
            MessageUtil.error(sender, "Use on or off.");
            return;
        }
        boolean on = value.equals("on");
        bot.setSneaking(on);
        FakePlayerController.setSneaking(bot.getEntity(), on, config.isForceSneakPose(), config.isFixedSneakPose());
        MessageUtil.success(sender, bot.getName() + " sneaking: " + on + ".");
    }

    private void handleSkin(CommandSender sender, String[] args) {
        Bot bot = requireBot(sender, args, 1);
        if (bot == null) return;
        if (args.length < 3) {
            MessageUtil.error(sender, "Usage: /bot skin <name> <online-player>");
            return;
        }
        Player source = Bukkit.getPlayerExact(args[2]);
        if (source == null) {
            MessageUtil.error(sender, "That player must be online.");
            return;
        }
        if (!manager.reskin(bot.getName(), source)) {
            MessageUtil.error(sender, "Could not apply the skin to " + bot.getName() + ".");
            return;
        }
        MessageUtil.success(sender, bot.getName() + " now uses " + source.getName() + "'s skin.");
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (args.length != 1) {
            MessageUtil.error(sender, "Usage: /bot reload");
            return;
        }
        BotPlugin.getInstance().reloadPluginConfig();
        MessageUtil.success(sender, "PlayerBots configuration reloaded.");
    }

    private Bot requireBot(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            MessageUtil.error(sender, "Missing bot name.");
            return null;
        }
        Bot bot = manager.get(args[index]);
        if (bot == null || !bot.isValid()) {
            MessageUtil.error(sender, "No such bot: " + args[index]);
            return null;
        }
        return bot;
    }

    private static Material parseMaterial(String raw) {
        return Material.matchMaterial(raw);
    }

    private static boolean contains(String[] args, String value) {
        return Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase(value));
    }

    private static String fmt(Location l) {
        return round(l.getX()) + ", " + round(l.getY()) + ", " + round(l.getZ()) + " in " + l.getWorld().getName();
    }

    private static double round(double d) { return Math.round(d * 100.0) / 100.0; }
    private static float round(float f) { return Math.round(f * 100f) / 100f; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bot.use") && !sender.hasPermission("bot.admin")) return List.of();
        if (args.length == 1) return startsWith(SUBCOMMANDS, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        int idx = args.length - 1;

        if (idx == 1 && sub.equals("rejoin")) {
            UUID requester = sender instanceof Player player ? player.getUniqueId() : null;
            return startsWith(manager.rejoinNames(requester), args[1]);
        }
        if (idx == 1 && !sub.equals("spawn")) return startsWith(manager.names(), args[1]);
        return switch (sub) {
            case "spawn" -> spawnCompletions(args, idx);
            case "original" -> originalCompletions(args, idx);
            case "attack", "use" -> idx == 2 ? startsWith(List.of("single", "continuously", "interval", "stop"), args[2]) : (idx == 3 && args[2].equalsIgnoreCase("interval") ? List.of("1", "5", "10", "20", "40") : List.of());
            case "hotbar" -> idx == 2 ? startsWith(List.of("0","1","2","3","4","5","6","7","8"), args[2]) : List.of();
            case "offhand" -> idx == 2 ? startsWith(offhandCompletions(), args[2]) : List.of();
            case "sneak" -> idx == 2 ? startsWith(List.of("on", "off"), args[2]) : List.of();
            case "skin" -> idx == 2 ? startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]) : List.of();
            case "kill_other" -> idx == 2 ? startsWith(manager.names(), args[2]) : List.of();
            case "dropstack" -> idx == 2 ? startsWith(itemNames(), args[2]) : List.of();
            default -> List.of();
        };
    }

    private List<String> originalCompletions(String[] args, int idx) {
        if (idx == 1) return startsWith(List.of("spawn", "grant", "revoke"), args[1]);
        if (idx == 2 && args.length > 2 && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("revoke"))) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        return List.of();
    }

    private List<String> spawnCompletions(String[] args, int idx) {
        if (idx == 1) return startsWith(List.of("at", "persistent"), args[1]);
        if (idx >= 5 && idx < args.length) {
            String last = args[idx - 1].toLowerCase(Locale.ROOT);
            if (last.equals("in")) {
                return startsWith(Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()), args[idx]);
            }
        }
        if (idx > 1) return startsWith(List.of("in", "facing", "persistent"), args[idx]);
        return List.of();
    }

    private List<String> offhandCompletions() {
        List<String> result = new ArrayList<>();
        result.add("clear");
        for (int i = 0; i < 36; i++) result.add(String.valueOf(i));
        return result;
    }

    private List<String> itemNames() {
        List<String> result = new ArrayList<>();
        for (Material material : Material.values()) if (material.isItem()) result.add(material.getKey().getKey());
        return result;
    }

    private static List<String> startsWith(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).sorted().limit(50).collect(Collectors.toList());
    }
}
