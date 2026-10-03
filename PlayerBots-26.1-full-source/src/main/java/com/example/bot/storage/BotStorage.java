package com.example.bot.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persists active persistent bots and offline/rejoin snapshots to plugins/PlayerBots/bots.yml.
 */
public class BotStorage {
    public static final class SavedBot {
        public String name;
        public UUID uuid;
        public UUID ownerUuid;
        public boolean original;
        public String world;
        public double x, y, z;
        public float yaw, pitch;
        /** Whether the bot should be persistent after it is rejoined. */
        public boolean persistent;
        public int heldSlot;
        public ItemStack[] inventory;
    }

    private final File file;
    private final Logger logger;
    /** Inventory carried across an explicit /bot kill -> /bot spawn cycle. */
    private final Map<String, ItemStack[]> keptInventories = new ConcurrentHashMap<>();

    public BotStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "bots.yml");
        this.logger = logger;
    }

    /** Backwards-compatible save method. */
    public synchronized void saveAll(List<SavedBot> persistentBots) {
        saveState(persistentBots, List.of());
    }

    /** Saves both persistent active bots and rejoin snapshots without losing either section. */
    public synchronized void saveState(List<SavedBot> persistentBots, List<SavedBot> rejoinBots) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("bots", null);
        cfg.set("rejoin", null);
        cfg.set("kept-inventories", null);

        for (SavedBot b : persistentBots) {
            writeBot(cfg, "bots." + b.name, b);
        }
        for (SavedBot b : rejoinBots) {
            writeBot(cfg, "rejoin." + b.name, b);
        }
        for (Map.Entry<String, ItemStack[]> entry : keptInventories.entrySet()) {
            cfg.set("kept-inventories." + entry.getKey(), cloneContents(entry.getValue()));
        }

        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                logger.warning("Could not create plugin data directory: " + file.getParentFile());
            }
            cfg.save(file);
        } catch (IOException e) {
            logger.severe("Failed to save bots.yml: " + e.getMessage());
        }
    }

    private static void writeBot(YamlConfiguration cfg, String path, SavedBot b) {
        cfg.set(path + ".uuid", b.uuid == null ? null : b.uuid.toString());
        if (b.ownerUuid != null) cfg.set(path + ".owner", b.ownerUuid.toString());
        cfg.set(path + ".original", b.original);
        cfg.set(path + ".world", b.world);
        cfg.set(path + ".x", b.x);
        cfg.set(path + ".y", b.y);
        cfg.set(path + ".z", b.z);
        cfg.set(path + ".yaw", b.yaw);
        cfg.set(path + ".pitch", b.pitch);
        cfg.set(path + ".persistent", b.persistent);
        cfg.set(path + ".heldSlot", b.heldSlot);
        List<ItemStack> items = new ArrayList<>();
        if (b.inventory != null) {
            for (ItemStack item : b.inventory) {
                items.add(item == null ? null : item.clone());
            }
        }
        cfg.set(path + ".inventory", items);
    }

    public synchronized List<SavedBot> loadAll() {
        return loadSection("bots");
    }

    public synchronized List<SavedBot> loadRejoinAll() {
        return loadSection("rejoin");
    }

    private List<SavedBot> loadSection(String sectionName) {
        List<SavedBot> result = new ArrayList<>();
        if (!file.exists()) return result;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var section = cfg.getConfigurationSection(sectionName);
        if (section == null) return result;

        for (String name : section.getKeys(false)) {
            try {
                String path = sectionName + "." + name;
                SavedBot b = new SavedBot();
                b.name = name;
                String uuid = cfg.getString(path + ".uuid");
                if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("missing UUID");
                b.uuid = UUID.fromString(uuid);
                String owner = cfg.getString(path + ".owner");
                b.ownerUuid = owner == null || owner.isBlank() ? null : UUID.fromString(owner);
                b.original = cfg.getBoolean(path + ".original", false);
                b.world = cfg.getString(path + ".world");
                b.x = cfg.getDouble(path + ".x");
                b.y = cfg.getDouble(path + ".y");
                b.z = cfg.getDouble(path + ".z");
                b.yaw = (float) cfg.getDouble(path + ".yaw");
                b.pitch = (float) cfg.getDouble(path + ".pitch");
                b.persistent = cfg.getBoolean(path + ".persistent", false);
                b.heldSlot = cfg.getInt(path + ".heldSlot", 0);
                List<?> rawInv = cfg.getList(path + ".inventory");
                if (rawInv != null) {
                    ItemStack[] inv = new ItemStack[rawInv.size()];
                    for (int i = 0; i < rawInv.size(); i++) {
                        Object o = rawInv.get(i);
                        inv[i] = o instanceof ItemStack item ? item.clone() : null;
                    }
                    b.inventory = inv;
                } else {
                    b.inventory = new ItemStack[0];
                }
                result.add(b);
            } catch (Exception e) {
                logger.warning("Skipping corrupt bot entry '" + name + "' in " + sectionName + ": " + e.getMessage());
            }
        }
        return result;
    }


    /** Loads inventories preserved by an explicit /bot kill. */
    public synchronized void loadKeptInventories() {
        keptInventories.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var section = cfg.getConfigurationSection("kept-inventories");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            try {
                List<?> raw = cfg.getList("kept-inventories." + name);
                if (raw == null) continue;
                ItemStack[] items = new ItemStack[raw.size()];
                for (int i = 0; i < raw.size(); i++) {
                    Object value = raw.get(i);
                    items[i] = value instanceof ItemStack item ? item.clone() : null;
                }
                keptInventories.put(key(name), items);
            } catch (Exception ex) {
                logger.warning("Skipping corrupt kept inventory for bot '" + name + "': " + ex.getMessage());
            }
        }
    }

    /** Stores a cloned inventory for the next spawn of this bot name. */
    public synchronized void keepInventory(String name, ItemStack[] inventory) {
        if (name == null || inventory == null) return;
        keptInventories.put(key(name), cloneContents(inventory));
    }

    /** Returns and removes the kept inventory for a bot name, if present. */
    public synchronized ItemStack[] takeKeptInventory(String name) {
        ItemStack[] inventory = keptInventories.remove(key(name));
        return inventory == null ? null : cloneContents(inventory);
    }

    private static String key(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    /** Resolves a saved bot's world, or null if that world is not loaded. */
    public Location resolveLocation(SavedBot b) {
        if (b == null || b.world == null || b.world.isBlank()) return null;
        World w = Bukkit.getWorld(b.world);
        if (w == null) {
            logger.warning("Bot '" + b.name + "' was saved in world '" + b.world
                    + "' which is not currently loaded - skipping this restore/rejoin attempt.");
            return null;
        }
        return new Location(w, b.x, b.y, b.z, b.yaw, b.pitch);
    }
}
