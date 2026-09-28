package com.example.bot;

import com.example.bot.bot.Bot;
import com.example.bot.bot.BotManager;
import com.example.bot.config.BotConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Handles fake-player lifecycle quirks that normally depend on a real client. */
public final class BotListener implements Listener {
    private final BotPlugin plugin;
    private final BotManager manager;
    private final BotConfig config;

    public BotListener(BotPlugin plugin, BotManager manager, BotConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bot bot = manager.get(player.getName());
        if (bot == null && !manager.isPendingBot(player.getUniqueId())) return;
        if (!config.isJoinMessageEnabled()) return;

        String message = config.getJoinMessageFormat()
                .replace("{prefix}", config.getJoinMessagePrefix())
                .replace("{name}", player.getName());
        event.joinMessage(null);
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(component));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerJoin(PlayerJoinEvent event) {
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;
        manager.markOwnerOnline(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Bot bot = manager.get(player.getName());
        if (bot == null) return;

        if (config.isLeaveMessageEnabled()) {
            String message = config.getLeaveMessageFormat()
                    .replace("{prefix}", config.getLeaveMessagePrefix())
                    .replace("{name}", player.getName());
            event.quitMessage(null);
            Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(component));
        }

        // Capture the bot's final location/state immediately, then let the
        // normal player removal complete. /bot rejoin can restore this exact
        // snapshot later. Explicit /bot kill calls are suppressed by the manager.
        if (config.isRejoinEnabled() && config.isRejoinSaveOnQuit() && manager.shouldCaptureRejoin(player)) {
            manager.scheduleRetireForRejoin(bot.getName(), "quit");
        } else {
            manager.scheduleRetireWithoutRejoin(bot.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.getByUuid(player.getUniqueId()) != null) return;

        var owned = manager.ownedBotsSubjectToOfflineTimeout(player.getUniqueId());
        if (owned.isEmpty()) return;

        manager.markOwnerOffline(player.getUniqueId());
        if (!config.isOwnerOfflineWarningEnabled() || !config.isOwnerOfflineTimeoutEnabled()) return;

        int minutes = config.getOwnerOfflineTimeoutMinutes();
        String unit = minutes == 1 ? "minute" : "minutes";
        String time = minutes + " " + unit;
        String message = config.getOwnerOfflineWarningFormat()
                .replace("{owner}", player.getName())
                .replace("{time}", time)
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{seconds}", String.valueOf(minutes * 60L))
                .replace("{count}", String.valueOf(owned.size()));
        Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBotKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        Bot bot = manager.get(player.getName());
        if (bot == null || !config.isRejoinEnabled() || !config.isRejoinSaveOnKick()) return;
        if (!manager.shouldCaptureRejoin(player)) return;
        // Save immediately while the kicked fake player's entity is still
        // available. The subsequent PlayerQuitEvent or manager tick performs
        // the actual removal.
        manager.saveForRejoin(bot.getName(), "kick");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBotAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Bot bot = manager.get(player.getName());
        if (bot == null || !config.isAdvancementMessageEnabled()) return;

        // Preserve vanilla behavior for advancements that would not normally
        // announce to chat (e.g. recipes/root advancements) unless the server
        // owner explicitly enables custom announcements for those too.
        if (config.isAdvancementOnlyIfVanillaAnnounces() && event.message() == null) return;

        String advancementName = LegacyComponentSerializer.legacyAmpersand()
                .serialize(event.getAdvancement().displayName());
        String advancementKey = event.getAdvancement().getKey().asString();
        String message = config.getAdvancementMessageFormat()
                .replace("{prefix}", config.getAdvancementMessagePrefix())
                .replace("{name}", player.getName())
                .replace("{advancement}", advancementName)
                .replace("{key}", advancementKey);

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        event.message(component);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        Bot bot = manager.get(player.getName());
        if (bot == null) return;
        if (event.getFinalDamage() >= player.getHealth()) scheduleLifecycleRemoval(bot.getName(), "death");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Bot bot = manager.get(player.getName());
        if (bot != null) scheduleLifecycleRemoval(bot.getName(), "death");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Bot bot = manager.get(event.getEntity().getName());
        if (bot == null) return;

        // Capture BEFORE any death/drop processing can clear the player's inventory.
        // The saved copy is consumed by the next /bot spawn <same-name>.
        manager.preserveInventoryForNextSpawn(bot, "death");

        if (config.isDeathKeepInventory()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
        }
        event.setKeepLevel(config.isDeathKeepLevel());
        if (!config.isDeathDropExperience()) event.setDroppedExp(0);
        scheduleLifecycleRemoval(bot.getName(), "death");
    }

    private void scheduleLifecycleRemoval(String botName, String reason) {
        if (config.isRejoinEnabled() && config.isRejoinSaveOnDeath()) {
            manager.scheduleRetireForRejoin(botName, reason);
        } else {
            manager.scheduleRetireWithoutRejoin(botName);
        }
    }
}
