package com.example.bot.config;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;

public class BotConfig {
    private boolean enabled;
    private int maxBots;
    private int maxBotsPerPlayer;
    private boolean persistentByDefault;
    private String spawnNamePrefix;
    private GameMode spawnGameMode;
    private boolean spawnInvulnerable;
    private boolean spawnCollidable;
    private boolean spawnGlowing;
    private boolean spawnGravity;
    private boolean spawnCanPickup;
    private int spawnStartingHotbarSlot;
    private boolean physicsEnabled;
    private double maxHealth;
    private double movementSpeed;
    private double attackSpeed;
    private double attackReach;
    private double knockbackResistance;
    private boolean attackDecreaseHunger;
    private boolean useDecreaseHunger;

    private boolean debug;
    private boolean logSpawns;
    private boolean logKills;
    private boolean logPickups;

    private boolean pickupEnabled;
    private boolean pickupUseVanillaPhysics;
    private double pickupRadius;
    private int pickupCheckInterval;
    private int pickupMaxItemsPerCheck;
    private boolean pickupRespectDelay;
    private boolean pickupRespectOwner;
    private boolean pickupPreferHotbar;
    private int pickupPreferredHotbarSlot;

    private boolean handSyncEnabled;
    private boolean handSyncOnPickup;
    private boolean handSyncOnHotbar;
    private boolean handSyncOnGive;

    private double dropVelocityMultiplier;
    private int dropPickupDelay;

    private boolean forceSneakPose;
    private boolean fixedSneakPose;

    private boolean deathKeepInventory;
    private boolean killKeepInventory;
    private boolean deathKeepLevel;
    private boolean deathDropExperience;

    private boolean knockbackEnabled;

    private boolean mineEnabled;
    private double mineReach;
    private boolean useMineFallback;

    private boolean useEnabled;
    private double useReach;
    private boolean useSwingHand;
    private boolean useAllowBlockItems;
    private boolean useAllowNonBlockItems;

    private boolean autoSaveEnabled;
    private int autoSaveIntervalTicks;

    private String messagePrefix;
    private String messageInfoColor;
    private String messageSuccessColor;
    private String messageErrorColor;
    private String messageWarnColor;

    private boolean joinMessageEnabled;
    private String joinMessagePrefix;
    private String joinMessageFormat;

    private boolean leaveMessageEnabled;
    private String leaveMessagePrefix;
    private String leaveMessageFormat;

    private boolean advancementMessageEnabled;
    private String advancementMessagePrefix;
    private String advancementMessageFormat;
    private boolean advancementOnlyIfVanillaAnnounces;

    private boolean ownerOfflineTimeoutEnabled;
    private int ownerOfflineTimeoutMinutes;
    private boolean ownerOfflineTimeoutExemptOriginal;
    private boolean ownerOfflineWarningEnabled;
    private String ownerOfflineWarningFormat;

    private boolean collisionEnabled;
    private String collisionTeamName;
    private String collisionRule;

    private boolean rejoinEnabled;
    private boolean rejoinPersistAfterRestart;
    private boolean rejoinSaveOnQuit;
    private boolean rejoinSaveOnKick;
    private boolean rejoinSaveOnDeath;
    private boolean rejoinOriginalBots;
    private boolean authMeBypassEnabled;
    private boolean luckPermsHidePrefix;
    private String luckPermsPrefix;

    public void load(FileConfiguration cfg) {
        enabled = cfg.getBoolean("enabled", true);
        maxBots = Math.max(1, cfg.getInt("limits.max-bots", 50));
        maxBotsPerPlayer = Math.max(1, Math.min(5, cfg.getInt("limits.max-bots-per-player", 3)));

        persistentByDefault = cfg.getBoolean("spawn.persistent-by-default", false);
        spawnNamePrefix = cfg.getString("spawn.default-name-prefix", "BOT_");
        spawnGameMode = parseGameMode(cfg.getString("spawn.game-mode", "SURVIVAL"));
        spawnInvulnerable = cfg.getBoolean("spawn.invulnerable", false);
        spawnCollidable = cfg.getBoolean("spawn.collidable", true);
        spawnGlowing = cfg.getBoolean("spawn.glowing", false);
        spawnGravity = cfg.getBoolean("spawn.gravity", true);
        spawnCanPickup = cfg.getBoolean("spawn.can-pickup-items", true);
        spawnStartingHotbarSlot = clampInt(cfg.getInt("spawn.starting-hotbar-slot", 0), 0, 8);
        physicsEnabled = cfg.getBoolean("physics.enabled", true);

        maxHealth = clamp(cfg.getDouble("attributes.max-health", 20.0), 1.0, 1024.0);
        movementSpeed = clamp(cfg.getDouble("attributes.movement-speed", 0.1), 0.0, 1.0);
        attackSpeed = clamp(cfg.getDouble("attributes.attack-speed", 4.0), 0.1, 1024.0);
        attackReach = clamp(cfg.getDouble("attack.reach-blocks", 4.0), 1.0, 6.0);
        mineEnabled = cfg.getBoolean("mine.enabled", true);
        mineReach = clamp(cfg.getDouble("mine.reach-blocks", 5.0), 1.0, 6.0);
        useMineFallback = cfg.getBoolean("use.mine-fallback", true);
        knockbackResistance = clamp(cfg.getDouble("attributes.knockback-resistance", 0.0), 0.0, 1.0);

        attackDecreaseHunger = cfg.getBoolean("hunger.attack", true);
        useDecreaseHunger = cfg.getBoolean("hunger.use", true);

        debug = cfg.getBoolean("debug", false);
        logSpawns = cfg.getBoolean("logging.spawns", true);
        logKills = cfg.getBoolean("logging.kills", true);
        logPickups = cfg.getBoolean("logging.pickups", false);

        pickupEnabled = cfg.getBoolean("pickup.enabled", true);
        pickupUseVanillaPhysics = cfg.getBoolean("pickup.use-vanilla-physics", true);
        pickupRadius = clamp(cfg.getDouble("pickup.radius", 1.5), 0.5, 8.0);
        pickupCheckInterval = Math.max(1, cfg.getInt("pickup.check-interval-ticks", 10));
        pickupMaxItemsPerCheck = Math.max(1, cfg.getInt("pickup.max-items-per-check", 1));
        pickupRespectDelay = cfg.getBoolean("pickup.respect-pickup-delay", true);
        pickupRespectOwner = cfg.getBoolean("pickup.respect-item-owner", true);
        pickupPreferHotbar = cfg.getBoolean("pickup.prefer-hotbar", true);
        pickupPreferredHotbarSlot = clampInt(cfg.getInt("pickup.preferred-hotbar-slot", -1), -1, 8);

        handSyncEnabled = cfg.getBoolean("hand-sync.enabled", true);
        handSyncOnPickup = cfg.getBoolean("hand-sync.on-pickup", true);
        handSyncOnHotbar = cfg.getBoolean("hand-sync.on-hotbar", true);
        handSyncOnGive = cfg.getBoolean("hand-sync.on-give", true);

        dropVelocityMultiplier = clamp(cfg.getDouble("drop.throw-velocity-multiplier", 1.0), 0.0, 5.0);
        dropPickupDelay = Math.max(0, cfg.getInt("drop.pickup-delay-ticks", 10));

        forceSneakPose = cfg.getBoolean("sneak.force-pose", true);
        fixedSneakPose = cfg.getBoolean("sneak.fixed-pose", true);

        deathKeepInventory = cfg.getBoolean("death.keep-inventory", true);
        killKeepInventory = cfg.getBoolean("kill.keep-inventory", true);
        deathKeepLevel = cfg.getBoolean("death.keep-level", true);
        deathDropExperience = cfg.getBoolean("death.drop-experience", false);

        knockbackEnabled = cfg.getBoolean("knockback.enabled", true);

        useEnabled = cfg.getBoolean("use.enabled", true);
        useReach = clamp(cfg.getDouble("use.reach-blocks", 5.0), 1.0, 6.0);
        useSwingHand = cfg.getBoolean("use.swing-hand", true);
        useAllowBlockItems = cfg.getBoolean("use.allow-block-items", true);
        useAllowNonBlockItems = cfg.getBoolean("use.allow-non-block-items", true);

        autoSaveEnabled = cfg.getBoolean("autosave.enabled", true);
        autoSaveIntervalTicks = Math.max(20, cfg.getInt("autosave.interval-ticks", 6000));

        messagePrefix = cfg.getString("messages.prefix", "&6[Bot] &r");
        messageInfoColor = cfg.getString("messages.info-color", "&7");
        messageSuccessColor = cfg.getString("messages.success-color", "&a");
        messageErrorColor = cfg.getString("messages.error-color", "&c");
        messageWarnColor = cfg.getString("messages.warn-color", "&e");

        joinMessageEnabled = cfg.getBoolean("join-message.enabled", true);
        joinMessagePrefix = cfg.getString("join-message.prefix", "&7[&6BOT&7] ");
        joinMessageFormat = cfg.getString("join-message.format", "{prefix}&e{name} &7joined the game.");

        leaveMessageEnabled = cfg.getBoolean("leave-message.enabled", true);
        leaveMessagePrefix = cfg.getString("leave-message.prefix", "&7[&6BOT&7] ");
        leaveMessageFormat = cfg.getString("leave-message.format", "{prefix}&e{name} &7left the game.");

        advancementMessageEnabled = cfg.getBoolean("advancement-message.enabled", true);
        advancementMessagePrefix = cfg.getString("advancement-message.prefix", "&7[&6BOT&7] ");
        advancementMessageFormat = cfg.getString("advancement-message.format", "{prefix}&e{name} &7has made the advancement &r{advancement}&7.");
        advancementOnlyIfVanillaAnnounces = cfg.getBoolean("advancement-message.only-if-vanilla-announces", true);

        ownerOfflineTimeoutEnabled = cfg.getBoolean("owner-offline-timeout.enabled", true);
        ownerOfflineTimeoutMinutes = clampInt(cfg.getInt("owner-offline-timeout.minutes", 15), 1, 1440);
        ownerOfflineTimeoutExemptOriginal = cfg.getBoolean("owner-offline-timeout.exempt-original-bots", true);
        ownerOfflineWarningEnabled = cfg.getBoolean("owner-offline-warning.enabled", true);
        ownerOfflineWarningFormat = cfg.getString("owner-offline-warning.format", "&e{owner} &7left the server. Their bot(s) will be kicked in &c{time}&7.");

        collisionEnabled = cfg.getBoolean("collision.enabled", true);
        collisionTeamName = cfg.getString("collision.team-name", "playerbots");
        if (collisionTeamName == null || collisionTeamName.isBlank()) collisionTeamName = "playerbots";
        if (collisionTeamName.length() > 32) collisionTeamName = collisionTeamName.substring(0, 32);
        collisionRule = cfg.getString("collision.rule", "ALWAYS");

        rejoinEnabled = cfg.getBoolean("rejoin.enabled", true);
        rejoinPersistAfterRestart = cfg.getBoolean("rejoin.persist-after-restart", true);
        rejoinSaveOnQuit = cfg.getBoolean("rejoin.save-on-quit", true);
        rejoinSaveOnKick = cfg.getBoolean("rejoin.save-on-kick", true);
        rejoinSaveOnDeath = cfg.getBoolean("rejoin.save-on-death", true);
        rejoinOriginalBots = cfg.getBoolean("rejoin.allow-original-bots", true);
        authMeBypassEnabled = cfg.getBoolean("authme.bypass", true);
        luckPermsHidePrefix = cfg.getBoolean("luckperms.hide-prefix", true);
        luckPermsPrefix = cfg.getString("luckperms.prefix", "&7[&6BOT&7] ");
    }

    private static GameMode parseGameMode(String raw) {
        if (raw == null) return GameMode.SURVIVAL;
        try {
            return GameMode.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxBots() { return maxBots; }
    public int getMaxBotsPerPlayer() { return maxBotsPerPlayer; }
    public boolean isPersistentByDefault() { return persistentByDefault; }
    public String getSpawnNamePrefix() { return spawnNamePrefix; }
    public GameMode getSpawnGameMode() { return spawnGameMode; }
    public boolean isSpawnInvulnerable() { return spawnInvulnerable; }
    public boolean isSpawnCollidable() { return spawnCollidable; }
    public boolean isSpawnGlowing() { return spawnGlowing; }
    public boolean isSpawnGravity() { return spawnGravity; }
    public boolean isSpawnCanPickup() { return spawnCanPickup; }
    public int getSpawnStartingHotbarSlot() { return spawnStartingHotbarSlot; }
    public boolean isPhysicsEnabled() { return physicsEnabled; }
    public double getMaxHealth() { return maxHealth; }
    public double getMovementSpeed() { return movementSpeed; }
    public double getAttackSpeed() { return attackSpeed; }
    public double getAttackReach() { return attackReach; }
    public double getKnockbackResistance() { return knockbackResistance; }
    public boolean isAttackDecreaseHunger() { return attackDecreaseHunger; }
    public boolean isUseDecreaseHunger() { return useDecreaseHunger; }
    public boolean isDebug() { return debug; }
    public boolean isLogSpawns() { return logSpawns; }
    public boolean isLogKills() { return logKills; }
    public boolean isLogPickups() { return logPickups; }
    public boolean isPickupEnabled() { return pickupEnabled; }
    public boolean isPickupUseVanillaPhysics() { return pickupUseVanillaPhysics; }
    public double getPickupRadius() { return pickupRadius; }
    public int getPickupCheckInterval() { return pickupCheckInterval; }
    public int getPickupMaxItemsPerCheck() { return pickupMaxItemsPerCheck; }
    public boolean isPickupRespectDelay() { return pickupRespectDelay; }
    public boolean isPickupRespectOwner() { return pickupRespectOwner; }
    public boolean isPickupPreferHotbar() { return pickupPreferHotbar; }
    public int getPickupPreferredHotbarSlot() { return pickupPreferredHotbarSlot; }
    public boolean isHandSyncEnabled() { return handSyncEnabled; }
    public boolean isHandSyncOnPickup() { return handSyncOnPickup; }
    public boolean isHandSyncOnHotbar() { return handSyncOnHotbar; }
    public boolean isHandSyncOnGive() { return handSyncOnGive; }
    public double getDropVelocityMultiplier() { return dropVelocityMultiplier; }
    public int getDropPickupDelay() { return dropPickupDelay; }
    public boolean isForceSneakPose() { return forceSneakPose; }
    public boolean isFixedSneakPose() { return fixedSneakPose; }
    public boolean isDeathKeepInventory() { return deathKeepInventory; }
    public boolean isKillKeepInventory() { return killKeepInventory; }
    public boolean isDeathKeepLevel() { return deathKeepLevel; }
    public boolean isDeathDropExperience() { return deathDropExperience; }
    public boolean isKnockbackEnabled() { return knockbackEnabled; }
    public boolean isUseEnabled() { return useEnabled; }
    public double getUseReach() { return useReach; }
    public boolean isUseSwingHand() { return useSwingHand; }
    public boolean isUseAllowBlockItems() { return useAllowBlockItems; }
    public boolean isUseAllowNonBlockItems() { return useAllowNonBlockItems; }
    public boolean isMineEnabled() { return mineEnabled; }
    public double getMineReach() { return mineReach; }
    public boolean isUseMineFallback() { return useMineFallback; }
    public boolean isLuckPermsHidePrefix() { return luckPermsHidePrefix; }
    public String getLuckPermsPrefix() { return luckPermsPrefix; }
    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
    public int getAutoSaveIntervalTicks() { return autoSaveIntervalTicks; }
    public String getMessagePrefix() { return messagePrefix; }
    public String getMessageInfoColor() { return messageInfoColor; }
    public String getMessageSuccessColor() { return messageSuccessColor; }
    public String getMessageErrorColor() { return messageErrorColor; }
    public String getMessageWarnColor() { return messageWarnColor; }
    public boolean isJoinMessageEnabled() { return joinMessageEnabled; }
    public String getJoinMessagePrefix() { return joinMessagePrefix; }
    public String getJoinMessageFormat() { return joinMessageFormat; }
    public boolean isLeaveMessageEnabled() { return leaveMessageEnabled; }
    public String getLeaveMessagePrefix() { return leaveMessagePrefix; }
    public String getLeaveMessageFormat() { return leaveMessageFormat; }
    public boolean isAdvancementMessageEnabled() { return advancementMessageEnabled; }
    public String getAdvancementMessagePrefix() { return advancementMessagePrefix; }
    public String getAdvancementMessageFormat() { return advancementMessageFormat; }
    public boolean isAdvancementOnlyIfVanillaAnnounces() { return advancementOnlyIfVanillaAnnounces; }
    public boolean isOwnerOfflineTimeoutEnabled() { return ownerOfflineTimeoutEnabled; }
    public int getOwnerOfflineTimeoutMinutes() { return ownerOfflineTimeoutMinutes; }
    public boolean isOwnerOfflineTimeoutExemptOriginal() { return ownerOfflineTimeoutExemptOriginal; }
    public boolean isOwnerOfflineWarningEnabled() { return ownerOfflineWarningEnabled; }
    public String getOwnerOfflineWarningFormat() { return ownerOfflineWarningFormat; }
    public boolean isCollisionEnabled() { return collisionEnabled; }
    public String getCollisionTeamName() { return collisionTeamName; }
    public String getCollisionRule() { return collisionRule; }
    public boolean isRejoinEnabled() { return rejoinEnabled; }
    public boolean isRejoinPersistAfterRestart() { return rejoinPersistAfterRestart; }
    public boolean isRejoinSaveOnQuit() { return rejoinSaveOnQuit; }
    public boolean isRejoinSaveOnKick() { return rejoinSaveOnKick; }
    public boolean isRejoinSaveOnDeath() { return rejoinSaveOnDeath; }
    public boolean isRejoinOriginalBots() { return rejoinOriginalBots; }
    public boolean isAuthMeBypassEnabled() { return authMeBypassEnabled; }
}
