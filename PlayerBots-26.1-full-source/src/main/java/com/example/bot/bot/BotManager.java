package com.example.bot.bot;

import com.example.bot.BotPlugin;
import com.example.bot.config.BotConfig;
import com.example.bot.nms.FakePlayerFactory;
import com.example.bot.storage.BotStorage;
import com.example.bot.util.LuckPermsBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BotManager {

    private final BotPlugin plugin;
    private final BotConfig config;
    private final BotStorage storage;

    /** Keyed by lower-cased bot name for case-insensitive lookup. */
    private final Map<String, Bot> bots = new ConcurrentHashMap<>();
    /** Snapshots of bots that have left/died/been kicked and can be brought back with /bot rejoin. */
    private final Map<String, BotStorage.SavedBot> rejoinable = new ConcurrentHashMap<>();
    /** Profiles currently being inserted; PlayerJoinEvent can fire before the Bot object is registered. */
    private final java.util.Set<UUID> pendingBotUuids = ConcurrentHashMap.newKeySet();
    /** Explicit removals such as /bot kill must not create rejoin snapshots from PlayerQuitEvent. */
    private final java.util.Set<UUID> suppressRejoin = ConcurrentHashMap.newKeySet();
    /** Prevent duplicate lifecycle-removal tasks from death/kick/quit events. */
    private final java.util.Set<String> pendingRetirements = ConcurrentHashMap.newKeySet();

    private BukkitTask aiTask;
    private BukkitTask pickupTask;
    private BukkitTask autosaveTask;
    private Team collisionTeam;

    public BotManager(BotPlugin plugin, BotConfig config, BotStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
    }

    public void start() {
        setupCollisionTeam();
        reloadScheduling();
        storage.loadKeptInventories();
        loadRejoinRecords();
        loadPersistedBots();
    }

    public void reloadScheduling() {
        if (aiTask != null) aiTask.cancel();
        if (pickupTask != null) pickupTask.cancel();
        if (autosaveTask != null) autosaveTask.cancel();
        aiTask = null;
        pickupTask = null;
        autosaveTask = null;
        setupCollisionTeam();
        com.example.bot.nms.FakePlayerFactory.setPhysicsEnabled(config.isPhysicsEnabled());
        com.example.bot.nms.FakePlayerFactory.setAuthMeBypassEnabled(config.isAuthMeBypassEnabled());
        if (config.isEnabled()) {
            for (Bot bot : bots.values()) {
                if (bot.getEntity() != null && bot.getEntity().isValid()) {
                    applySpawnSettings(bot.getEntity());
                    if (config.isHandSyncEnabled()) com.example.bot.nms.FakePlayerController.syncHeldItem(bot.getEntity());
                }
            }
        }
        if (!config.isEnabled()) return;
        aiTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAI, 1L, 1L);
        pickupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPickup,
                1L, config.getPickupCheckInterval());
        if (config.isAutoSaveEnabled()) {
            autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAllPersistent,
                    config.getAutoSaveIntervalTicks(), config.getAutoSaveIntervalTicks());
        }
    }

    public void shutdown() {
        if (aiTask != null) aiTask.cancel();
        if (pickupTask != null) pickupTask.cancel();
        if (autosaveTask != null) autosaveTask.cancel();
        saveAllPersistent();
        // Remove every fake player cleanly so the server doesn't try to
        // persist a "player" whose entity object is about to vanish.
        for (Bot bot : bots.values()) {
            if (bot.getEntity() != null) {
                suppressRejoin.add(bot.getUuid());
                bot.getEntity().setPersistent(false);
                removeFromCollisionTeam(bot.getEntity());
                FakePlayerFactory.removeFakePlayer(bot.getEntity());
            }
        }
        suppressRejoin.clear();
        bots.clear();
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    public enum SpawnResult { OK, DUPLICATE_NAME, LIMIT_REACHED, INVALID_NAME }

    public SpawnResult spawn(String name, Location location, boolean persistent) {
        return spawn(name, location, persistent, null, null);
    }

    public SpawnResult spawn(String name, Location location, boolean persistent, Player skinOwner) {
        return spawn(name, location, persistent, skinOwner, null);
    }

    public SpawnResult spawn(String name, Location location, boolean persistent, Player skinOwner, UUID ownerUuid) {
        return spawn(name, location, persistent, skinOwner, ownerUuid, false);
    }

    public SpawnResult spawn(String name, Location location, boolean persistent, Player skinOwner, UUID ownerUuid, boolean original) {
        if (name == null || name.isBlank() || name.length() > 16) {
            return SpawnResult.INVALID_NAME;
        }
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (bots.containsKey(key)) {
            return SpawnResult.DUPLICATE_NAME;
        }
        if (bots.size() >= config.getMaxBots()) {
            return SpawnResult.LIMIT_REACHED;
        }
        if (!original && ownerUuid != null && countByOwner(ownerUuid) >= config.getMaxBotsPerPlayer()) {
            return SpawnResult.LIMIT_REACHED;
        }

        UUID uuid = offlineBotUuid(name);
        pendingBotUuids.add(uuid);
        final Player entity;
        try {
            entity = FakePlayerFactory.createFakePlayer(name, uuid, skinOwner, location);
            // PlayerList normally persists real players to world/playerdata.
            // Bots own their data in PlayerBots/bots.yml instead, so do not let
            // the fake player create/update a vanilla playerdata file.
            entity.setPersistent(false);
            applySpawnSettings(entity);
            entity.getInventory().setHeldItemSlot(config.getSpawnStartingHotbarSlot());
            Bot bot = new Bot(name, uuid, entity, persistent, ownerUuid, original);
            bots.put(key, bot);
            LuckPermsBridge.applyBotPrefix(plugin, uuid, name, config.isLuckPermsHidePrefix(), config.getLuckPermsPrefix());
            rejoinable.remove(key);
            pendingBotUuids.remove(uuid);

            // An explicit /bot kill is not a Minecraft death, so PlayerDeathEvent
            // never fires. Carry the bot inventory forward to the next /bot spawn
            // of the same name, just like keep-inventory would for a real death.
            ItemStack[] keptInventory = storage.takeKeptInventory(name);
            if (keptInventory != null) {
                restoreInventory(entity, keptInventory);
            }

            if (config.isHandSyncEnabled()) {
                com.example.bot.nms.FakePlayerController.syncHeldItem(entity);
            }
            if (keptInventory != null) {
                // Persist removal of the one-shot kept inventory immediately so a
                // restart cannot duplicate the restored items.
                saveAllPersistent();
            }
        } catch (RuntimeException | Error ex) {
            pendingBotUuids.remove(uuid);
            throw ex;
        }
        if (config.isLogSpawns() || config.isDebug()) {
            plugin.getLogger().info("Spawned bot '" + name + "' at " + location.getWorld().getName()
                    + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ() + ".");
        }

        if (persistent) {
            saveAllPersistent();
        }
        return SpawnResult.OK;
    }

    /**
     * Re-creates a bot from saved data (used on startup for persistent
     * bots). Restores inventory and held slot after the fresh entity is
     * placed.
     */
    private void spawnFromSaved(BotStorage.SavedBot saved, Location location) {
        String key = saved.name.toLowerCase(java.util.Locale.ROOT);
        if (bots.containsKey(key)) return;

        pendingBotUuids.add(saved.uuid);
        final Player entity;
        try {
            entity = FakePlayerFactory.createFakePlayer(saved.name, saved.uuid, null, location);
            entity.setPersistent(false);
            applySpawnSettings(entity);
            Bot bot = new Bot(saved.name, saved.uuid, entity, saved.persistent, saved.ownerUuid, saved.original);
            bots.put(key, bot);
            LuckPermsBridge.applyBotPrefix(plugin, saved.uuid, saved.name, config.isLuckPermsHidePrefix(), config.getLuckPermsPrefix());
            rejoinable.remove(key);
            pendingBotUuids.remove(saved.uuid);
        } catch (RuntimeException | Error ex) {
            pendingBotUuids.remove(saved.uuid);
            throw ex;
        }

        if (saved.inventory != null) {
            for (int i = 0; i < saved.inventory.length && i < entity.getInventory().getSize(); i++) {
                ItemStack item = saved.inventory[i];
                if (item != null) {
                    entity.getInventory().setItem(i, item);
                }
            }
        }
        if (saved.heldSlot >= 0 && saved.heldSlot <= 8) {
            entity.getInventory().setHeldItemSlot(saved.heldSlot);
        }
        if (config.isHandSyncEnabled()) {
            com.example.bot.nms.FakePlayerController.syncHeldItem(entity);
        }
    }

    private static void restoreInventory(Player entity, ItemStack[] inventory) {
        if (entity == null || inventory == null) return;
        int size = entity.getInventory().getSize();
        for (int i = 0; i < inventory.length && i < size; i++) {
            ItemStack item = inventory[i];
            entity.getInventory().setItem(i, item == null ? null : item.clone());
        }
    }

    public boolean isPendingBot(UUID uuid) {
        return uuid != null && pendingBotUuids.contains(uuid);
    }

    private void applySpawnSettings(Player entity) {
        entity.setGameMode(config.getSpawnGameMode());
        entity.setCanPickupItems(config.isSpawnCanPickup());
        entity.setCollidable(config.isCollisionEnabled() && config.isSpawnCollidable());
        applyCollisionTeam(entity);
        entity.setGlowing(config.isSpawnGlowing());
        entity.setInvulnerable(config.isSpawnInvulnerable());
        entity.setGravity(config.isSpawnGravity());

        var maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(config.getMaxHealth());
            entity.setHealth(config.getMaxHealth());
        }
        var movement = entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(config.getMovementSpeed());
        var attack = entity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
        if (attack != null) attack.setBaseValue(config.getAttackSpeed());
        var kb = entity.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(config.isKnockbackEnabled() ? config.getKnockbackResistance() : 1.0);
    }

    /**
     * Captures the bot's current inventory for the next spawn of the same
     * name. This is deliberately independent of Bukkit's normal death event
     * flow so /bot kill, /bot kill_other, and all damage-based deaths use the
     * exact same preservation path.
     */
    public boolean preserveInventoryForNextSpawn(Bot bot, String reason) {
        if (bot == null || bot.getEntity() == null || !config.isKillKeepInventory()) return false;
        storage.keepInventory(bot.getName(), bot.getEntity().getInventory().getContents());
        if (config.isDebug()) {
            plugin.getLogger().info("Saved inventory for bot '" + bot.getName() + "' for its next spawn (" + reason + ").");
        }
        return true;
    }

    public boolean kill(String name) {
        String key = key(name);
        Bot bot = bots.get(key);
        if (bot == null) return false;

        // /bot kill is an explicit removal, not a player death event. Preserve
        // the current inventory separately so the next /bot spawn <same-name>
        // can restore it when keep-inventory is enabled.
        boolean keptInventory = preserveInventoryForNextSpawn(bot, "kill");

        // Keep the Bot registered until PlayerList.remove() has finished.
        // Paper can fire PlayerQuitEvent synchronously during that removal,
        // and the leave-message listener needs to be able to identify the
        // departing fake player at that moment.
        if (bot.getEntity() != null) {
            suppressRejoin.add(bot.getUuid());
            bot.getEntity().setPersistent(false);
            removeFromCollisionTeam(bot.getEntity());
            FakePlayerFactory.removeFakePlayer(bot.getEntity());
            Bukkit.getScheduler().runTaskLater(plugin, () -> suppressRejoin.remove(bot.getUuid()), 2L);
        }
        bots.remove(key, bot);

        if (config.isLogKills() || config.isDebug()) {
            plugin.getLogger().info("Removed bot '" + bot.getName() + "'.");
        }
        if (bot.isPersistent() || keptInventory) {
            saveAllPersistent();
        }
        return true;
    }

    public Bot getOnlyOwnedBot(UUID ownerUuid) {
        Bot found = null;
        for (Bot bot : bots.values()) {
            if (!java.util.Objects.equals(ownerUuid, bot.getOwnerUuid())) continue;
            if (found != null) return null;
            found = bot;
        }
        return found;
    }

    public boolean isOwnedBy(Bot bot, UUID ownerUuid) {
        return bot != null && java.util.Objects.equals(ownerUuid, bot.getOwnerUuid());
    }

    public boolean killOther(String name, UUID requesterUuid) {
        Bot bot = get(name);
        if (bot == null) return false;
        if (requesterUuid != null && requesterUuid.equals(bot.getOwnerUuid())) return false;
        return kill(bot.getName());
    }

    public int killAll() {
        int count = 0;
        for (Bot bot : new ArrayList<>(bots.values())) {
            if (kill(bot.getName())) count++;
        }
        return count;
    }

    // ---------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------

    public Bot get(String name) {
        return bots.get(key(name));
    }

    public Bot getByUuid(UUID uuid) {
        if (uuid == null) return null;
        for (Bot bot : bots.values()) {
            if (uuid.equals(bot.getUuid())) return bot;
        }
        return null;
    }

    public Collection<Bot> all() {
        return bots.values();
    }

    public List<String> names() {
        List<String> result = new ArrayList<>();
        for (Bot b : bots.values()) result.add(b.getName());
        return result;
    }

    public boolean hasOwnedOriginal(UUID ownerUuid) {
        if (ownerUuid == null) return false;
        for (Bot bot : bots.values()) {
            if (bot.isOriginal() && ownerUuid.equals(bot.getOwnerUuid())) return true;
        }
        return false;
    }

    public int countByOwner(UUID ownerUuid) {
        if (ownerUuid == null) return 0;
        int count = 0;
        for (Bot bot : bots.values()) {
            if (ownerUuid.equals(bot.getOwnerUuid())) count++;
        }
        return count;
    }

    /** Returns bots owned by a player that are subject to the offline timeout. */
    public List<Bot> ownedBotsSubjectToOfflineTimeout(UUID ownerUuid) {
        List<Bot> result = new ArrayList<>();
        if (ownerUuid == null) return result;
        for (Bot bot : bots.values()) {
            if (!ownerUuid.equals(bot.getOwnerUuid())) continue;
            if (bot.isOriginal() && config.isOwnerOfflineTimeoutExemptOriginal()) continue;
            result.add(bot);
        }
        return result;
    }

    /** Starts the owner-offline timer immediately when the owner quits. */
    public void markOwnerOffline(UUID ownerUuid) {
        long now = System.currentTimeMillis();
        for (Bot bot : ownedBotsSubjectToOfflineTimeout(ownerUuid)) {
            if (bot.getOwnerOfflineSinceMillis() == 0L) bot.setOwnerOfflineSinceMillis(now);
        }
    }

    /** Clears the owner-offline timer immediately when the owner rejoins. */
    public void markOwnerOnline(UUID ownerUuid) {
        if (ownerUuid == null) return;
        for (Bot bot : bots.values()) {
            if (ownerUuid.equals(bot.getOwnerUuid())) bot.setOwnerOfflineSinceMillis(0L);
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }

    /** Deterministic UUID derived from the bot's name, so a given bot name always maps to the same fake-player identity across restarts (stable tab-list/scoreboard entries for persistent bots). */
    private static UUID offlineBotUuid(String name) {
        return UUID.nameUUIDFromBytes(("BotPluginFakePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------
    // Persistence / rejoin snapshots
    // ---------------------------------------------------------------

    private void loadPersistedBots() {
        for (BotStorage.SavedBot saved : storage.loadAll()) {
            if (!saved.persistent) continue;
            Location loc = storage.resolveLocation(saved);
            if (loc == null) continue;
            try {
                spawnFromSaved(saved, loc);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to restore persistent bot '" + saved.name + "': " + e.getMessage());
            }
        }
    }

    private void loadRejoinRecords() {
        if (!config.isRejoinEnabled() || !config.isRejoinPersistAfterRestart()) return;
        for (BotStorage.SavedBot saved : storage.loadRejoinAll()) {
            if (!config.isRejoinOriginalBots() && saved.original) continue;
            if (bots.containsKey(key(saved.name))) continue;
            rejoinable.put(key(saved.name), saved);
        }
        if (config.isDebug() && !rejoinable.isEmpty()) {
            plugin.getLogger().info("Loaded " + rejoinable.size() + " bot rejoin snapshot(s).");
        }
    }

    /** Saves live persistent bots and all rejoin snapshots together so one save cannot erase the other section. */
    public void saveAllPersistent() {
        List<BotStorage.SavedBot> persistent = new ArrayList<>();
        for (Bot bot : bots.values()) {
            if (!bot.isPersistent() || bot.getEntity() == null || !bot.getEntity().isValid()) continue;
            BotStorage.SavedBot s = snapshot(bot);
            s.persistent = true;
            persistent.add(s);
        }

        List<BotStorage.SavedBot> rejoin = new ArrayList<>();
        if (config.isRejoinEnabled() && config.isRejoinPersistAfterRestart()) {
            rejoin.addAll(rejoinable.values());
        }
        storage.saveState(persistent, rejoin);
    }

    private BotStorage.SavedBot snapshot(Bot bot) {
        BotStorage.SavedBot s = new BotStorage.SavedBot();
        s.name = bot.getName();
        s.uuid = bot.getUuid();
        s.ownerUuid = bot.getOwnerUuid();
        s.original = bot.isOriginal();
        Location loc = bot.getEntity().getLocation();
        s.world = loc.getWorld().getName();
        s.x = loc.getX();
        s.y = loc.getY();
        s.z = loc.getZ();
        s.yaw = loc.getYaw();
        s.pitch = loc.getPitch();
        s.persistent = bot.isPersistent();
        s.heldSlot = bot.getEntity().getInventory().getHeldItemSlot();
        ItemStack[] contents = bot.getEntity().getInventory().getContents();
        s.inventory = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            s.inventory[i] = contents[i] == null ? null : contents[i].clone();
        }
        return s;
    }

    /** Captures a bot's last known state so it can later be restored with /bot rejoin. */
    public boolean saveForRejoin(String name, String reason) {
        Bot bot = get(name);
        if (bot == null || bot.getEntity() == null || !config.isRejoinEnabled()) return false;
        if (bot.isOriginal() && !config.isRejoinOriginalBots()) return false;
        if (!pendingRetirements.contains(key(name)) && !shouldCaptureRejoin(bot.getEntity())) return false;
        rejoinable.put(key(name), snapshot(bot));
        if (config.isDebug()) {
            plugin.getLogger().info("Saved rejoin snapshot for '" + name + "' (" + reason + ").");
        }
        saveAllPersistent();
        return true;
    }

    public boolean shouldCaptureRejoin(Player player) {
        return player != null && !suppressRejoin.contains(player.getUniqueId());
    }

    /** Captures immediately, then removes the fake player safely on the next tick. */
    public void scheduleRetireForRejoin(String name, String reason) {
        String key = key(name);
        if (!pendingRetirements.add(key)) return;
        if (config.isRejoinEnabled()) saveForRejoin(name, reason);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bot bot = get(name);
                if (bot != null) kill(bot.getName());
            } finally {
                pendingRetirements.remove(key);
            }
        });
    }

    /** Removes a bot on the next tick without creating a rejoin snapshot. */
    public void scheduleRetireWithoutRejoin(String name) {
        String key = key(name);
        if (!pendingRetirements.add(key)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bot bot = get(name);
                if (bot != null) kill(bot.getName());
            } finally {
                pendingRetirements.remove(key);
            }
        });
    }

    public BotStorage.SavedBot getRejoinRecord(String name) {
        return rejoinable.get(key(name));
    }

    public boolean isRejoinOwnedBy(BotStorage.SavedBot saved, UUID ownerUuid) {
        return saved != null && ownerUuid != null && java.util.Objects.equals(ownerUuid, saved.ownerUuid);
    }

    public BotStorage.SavedBot getOnlyOwnedRejoin(UUID ownerUuid) {
        BotStorage.SavedBot found = null;
        for (BotStorage.SavedBot saved : rejoinable.values()) {
            if (!java.util.Objects.equals(ownerUuid, saved.ownerUuid)) continue;
            if (found != null) return null;
            found = saved;
        }
        return found;
    }

    public List<String> rejoinNames(UUID requester) {
        List<String> result = new ArrayList<>();
        for (BotStorage.SavedBot saved : rejoinable.values()) {
            if (requester == null || java.util.Objects.equals(requester, saved.ownerUuid)) {
                result.add(saved.name);
            }
        }
        return result;
    }

    public enum RejoinResult { OK, NOT_FOUND, ACTIVE_NAME, LIMIT_REACHED, WORLD_UNAVAILABLE, ORIGINAL_DISABLED, FAILED }

    public RejoinResult rejoin(String name, UUID requester) {
        String key = key(name);
        BotStorage.SavedBot saved = rejoinable.get(key);
        if (saved == null) return RejoinResult.NOT_FOUND;
        if (saved.original && !config.isRejoinOriginalBots()) return RejoinResult.ORIGINAL_DISABLED;
        if (bots.containsKey(key)) return RejoinResult.ACTIVE_NAME;
        if (bots.size() >= config.getMaxBots()) return RejoinResult.LIMIT_REACHED;
        if (!saved.original && saved.ownerUuid != null && countByOwner(saved.ownerUuid) >= config.getMaxBotsPerPlayer()) {
            return RejoinResult.LIMIT_REACHED;
        }
        Location location = storage.resolveLocation(saved);
        if (location == null) return RejoinResult.WORLD_UNAVAILABLE;
        try {
            // Rejoin uses the exact saved world/position/rotation instead of
            // treating the bot like a fresh spawn at the owner location.
            spawnFromSaved(saved, location);
            rejoinable.remove(key);
            saveAllPersistent();

            // Some server plugins react to the synthetic PlayerJoinEvent and
            // may schedule their own teleport after the fake player is created.
            // Re-assert the saved location on the next tick so /bot rejoin
            // reliably ends at the bot's last location from when it left.
            enforceRejoinLocation(name, location);
            return RejoinResult.OK;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to rejoin bot '" + name + "': " + ex.getMessage());
            return RejoinResult.FAILED;
        }
    }

    /**
     * Re-applies the saved location one tick after the synthetic login.
     * The delayed pass protects /bot rejoin from plugins that perform a
     * post-join teleport after FakePlayerFactory has already placed the bot.
     */
    private void enforceRejoinLocation(String name, Location savedLocation) {
        if (savedLocation == null || savedLocation.getWorld() == null) return;
        Location target = savedLocation.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bot bot = get(name);
            if (bot == null || bot.getEntity() == null || !bot.getEntity().isValid()) return;
            try {
                bot.getEntity().teleport(target);
                if (config.isCollisionEnabled()) {
                    bot.getEntity().setCollidable(config.isSpawnCollidable());
                    applyCollisionTeam(bot.getEntity());
                }
                if (config.isHandSyncEnabled()) {
                    com.example.bot.nms.FakePlayerController.syncHeldItem(bot.getEntity());
                }
            } catch (Throwable ex) {
                if (config.isDebug()) {
                    plugin.getLogger().warning("Could not re-apply saved location for bot '"
                            + name + "': " + (ex.getMessage() == null
                            ? ex.getClass().getSimpleName() : ex.getMessage()));
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // Tick loops
    // ---------------------------------------------------------------

    private void tickAI() {
        long now = System.currentTimeMillis();
        for (Bot bot : new ArrayList<>(bots.values())) {
            if (bot.getEntity() == null) continue;
            if (bot.getEntity().isDead() || !bot.getEntity().isValid() || bot.getEntity().getHealth() <= 0.0) {
                scheduleRetireForRejoin(bot.getName(), "dead/invalid");
                continue;
            }

            // Keep configured player-style collision active. For real player
            // collision, the dedicated scoreboard team below is the important
            // part because client-side prediction can ignore setCollidable().
            if (config.isCollisionEnabled()) {
                bot.getEntity().setCollidable(config.isSpawnCollidable());
                applyCollisionTeam(bot.getEntity());
            }

            // Regular player-owned bots have a 15-minute owner-presence leash
            // by default. Original bots are intentionally exempt.
            if (config.isOwnerOfflineTimeoutEnabled()
                    && bot.getOwnerUuid() != null
                    && (!bot.isOriginal() || !config.isOwnerOfflineTimeoutExemptOriginal())) {
                Player owner = Bukkit.getPlayer(bot.getOwnerUuid());
                if (owner != null && owner.isOnline()) {
                    bot.setOwnerOfflineSinceMillis(0L);
                } else {
                    long since = bot.getOwnerOfflineSinceMillis();
                    if (since == 0L) {
                        bot.setOwnerOfflineSinceMillis(now);
                    } else {
                        long timeout = config.getOwnerOfflineTimeoutMinutes() * 60_000L;
                        if (now - since >= timeout) {
                            String botName = bot.getName();
                            scheduleRetireForRejoin(botName, "owner-offline-timeout");
                            if (config.isDebug()) {
                                plugin.getLogger().info("Removed bot '" + botName
                                        + "' because its owner was offline for "
                                        + config.getOwnerOfflineTimeoutMinutes() + " minute(s).");
                            }
                            continue;
                        }
                    }
                }
            }

            // Execute any repeating command action. The action itself runs on the
            // normal server thread and is never implemented by teleporting or
            // forcing velocity/position.
            bot.tickActionTimer();
            if (bot.actionReady()) {
                BotAction action = bot.getCurrentAction();
                boolean executed = false;
                try {
                    executed = switch (action) {
                        case ATTACK -> BotAttack.attackOnce(bot, config.getAttackReach(), config);
                        case USE -> BotUse.useOnce(bot, config);
                        case MINE -> BotMine.mineOnce(bot, config);
                        default -> false;
                    };
                } catch (Throwable ex) {
                    // A malformed interaction target or a compatibility issue
                    // must not kill the scheduler task for every other bot.
                    if (config.isDebug()) {
                        plugin.getLogger().warning("Bot '" + bot.getName() + "' action " + action
                                + " failed: " + (ex.getMessage() == null
                                ? ex.getClass().getSimpleName() : ex.getMessage()));
                    }
                }
                bot.markActionExecuted();
                if (!executed && config.isDebug()) {
                    plugin.getLogger().fine("Bot '" + bot.getName() + "' could not execute "
                            + action + " right now.");
                }
            }

            // ServerPlayer can recompute its pose from the stored input state.
            if (bot.isSneaking()) {
                com.example.bot.nms.FakePlayerController.setSneaking(
                        bot.getEntity(), true, config.isForceSneakPose(), config.isFixedSneakPose());
            }
        }
    }

    private void tickPickup() {
        if (!config.isPickupEnabled() || config.isPickupUseVanillaPhysics()) return;
        for (Bot bot : new ArrayList<>(bots.values())) {
            if (!bot.isValid()) continue;
            int picked = BotInventory.pickupNearby(bot, config);
            if (picked > 0 && (config.isLogPickups() || config.isDebug())) {
                plugin.getLogger().info("Bot '" + bot.getName() + "' picked up " + picked + " item(s).");
            }
        }
    }

    private void setupCollisionTeam() {
        if (!config.isCollisionEnabled()) return;
        var scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) return;
        Scoreboard scoreboard = scoreboardManager.getMainScoreboard();

        String base = config.getCollisionTeamName();
        String desired = base;
        Team team = scoreboard.getTeam(desired);
        int attempt = 0;
        while (team != null && !team.getEntries().isEmpty() && attempt < 20) {
            String suffix = "_" + (attempt + 1);
            int maxBase = Math.max(1, 16 - suffix.length());
            desired = base.substring(0, Math.min(base.length(), maxBase)) + suffix;
            team = scoreboard.getTeam(desired);
            attempt++;
        }
        if (team == null) {
            team = scoreboard.registerNewTeam(desired);
        }

        OptionStatus status;
        try {
            status = OptionStatus.valueOf(config.getCollisionRule().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            status = OptionStatus.ALWAYS;
            if (config.isDebug()) {
                plugin.getLogger().warning("Invalid collision.rule '" + config.getCollisionRule()
                        + "'; using ALWAYS.");
            }
        }
        try {
            team.setOption(Option.COLLISION_RULE, status);
        } catch (IllegalArgumentException ignored) {
            team.setOption(Option.COLLISION_RULE, OptionStatus.ALWAYS);
        }
        collisionTeam = team;
    }

    private void applyCollisionTeam(Player entity) {
        if (!config.isCollisionEnabled() || collisionTeam == null || entity == null) return;
        try {
            collisionTeam.addEntity(entity);
        } catch (IllegalStateException ignored) {
            setupCollisionTeam();
            if (collisionTeam != null) {
                try { collisionTeam.addEntity(entity); } catch (Throwable ignoredAgain) { }
            }
        }
    }

    private void removeFromCollisionTeam(Player entity) {
        if (collisionTeam == null || entity == null) return;
        try { collisionTeam.removeEntity(entity); } catch (Throwable ignored) { }
    }

    public boolean reskin(String name, Player skinOwner) {
        Bot bot = get(name);
        if (bot == null || !bot.isValid() || skinOwner == null) return false;

        Location loc = bot.getEntity().getLocation();
        boolean persistent = bot.isPersistent();
        ItemStack[] inventory = bot.getEntity().getInventory().getContents();
        int heldSlot = bot.getEntity().getInventory().getHeldItemSlot();

        if (!kill(name)) return false;
        SpawnResult result = spawn(name, loc, persistent, skinOwner);
        if (result != SpawnResult.OK) return false;

        Bot recreated = get(name);
        if (recreated == null) return false;
        for (int i = 0; i < inventory.length && i < recreated.getEntity().getInventory().getSize(); i++) {
            ItemStack item = inventory[i];
            if (item != null) recreated.getEntity().getInventory().setItem(i, item);
        }
        recreated.getEntity().getInventory().setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));
        if (config.isHandSyncEnabled()) {
            com.example.bot.nms.FakePlayerController.syncHeldItem(recreated.getEntity());
        }
        return true;
    }

}
