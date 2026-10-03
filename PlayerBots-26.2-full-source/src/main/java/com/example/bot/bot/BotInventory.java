package com.example.bot.bot;

import com.example.bot.config.BotConfig;
import com.example.bot.nms.FakePlayerController;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BotInventory {
    private BotInventory() {}

    public static boolean give(Bot bot, Material material, int amount, BotConfig config) {
        if (amount <= 0) return false;
        Player player = bot.getEntity();
        Map<Integer, ItemStack> leftover = addLikeNormalPlayer(player.getInventory(), new ItemStack(material, amount), config);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        if (config.isHandSyncEnabled() && config.isHandSyncOnGive()) {
            FakePlayerController.syncHeldItem(player);
        }
        return leftover.isEmpty();
    }

    public static boolean holdSlot(Bot bot, int hotbarSlot, BotConfig config) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return false;
        Player player = bot.getEntity();
        player.getInventory().setHeldItemSlot(hotbarSlot);
        if (config.isHandSyncEnabled() && config.isHandSyncOnHotbar()) {
            FakePlayerController.syncHeldItem(player);
        }
        return true;
    }

    /** Swap a normal inventory slot with the bot's offhand slot. */
    public static boolean swapWithOffhand(Bot bot, int slot, BotConfig config) {
        if (slot < 0 || slot >= 36) return false;
        Player player = bot.getEntity();
        PlayerInventory inv = player.getInventory();
        ItemStack source = inv.getItem(slot);
        ItemStack offhand = inv.getItemInOffHand();
        inv.setItem(slot, offhand == null ? null : offhand.clone());
        inv.setItemInOffHand(source == null ? null : source.clone());
        if (config.isHandSyncEnabled()) FakePlayerController.syncHeldItem(player);
        return true;
    }

    /** Clears the bot's offhand and drops its contents naturally. */
    public static boolean clearOffhand(Bot bot, BotConfig config) {
        Player player = bot.getEntity();
        PlayerInventory inv = player.getInventory();
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand == null || offhand.isEmpty()) return false;
        ItemStack copy = offhand.clone();
        inv.setItemInOffHand(null);
        player.getWorld().dropItemNaturally(player.getLocation(), copy);
        if (config.isHandSyncEnabled()) FakePlayerController.syncHeldItem(player);
        return true;
    }

    public static boolean dropHeld(Bot bot, boolean all, BotConfig config) {
        Player player = bot.getEntity();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) return false;
        int amount = all ? held.getAmount() : 1;
        Item dropped = player.dropItem(EquipmentSlot.HAND, amount, false,
                item -> item.setThrower(player.getUniqueId()));
        if (dropped == null) return false;
        tuneDrop(dropped, config);
        if (config.isHandSyncEnabled()) FakePlayerController.syncHeldItem(player);
        return true;
    }

    public static boolean dropStackOf(Bot bot, Material material, BotConfig config) {
        Player player = bot.getEntity();
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.isEmpty() && item.getType() == material) {
                Item dropped = player.dropItem(slot, item.getAmount(), false,
                        entity -> entity.setThrower(player.getUniqueId()));
                if (dropped != null) {
                    tuneDrop(dropped, config);
                    if (config.isHandSyncEnabled()) FakePlayerController.syncHeldItem(player);
                    return true;
                }
            }
        }
        return false;
    }

    public static void dropAll(Bot bot, BotConfig config) {
        Player player = bot.getEntity();
        PlayerInventory inv = player.getInventory();
        int storageSize = inv.getStorageContents().length;
        for (int slot = 0; slot < storageSize; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.isEmpty()) continue;
            Item dropped = player.dropItem(slot, item.getAmount(), false,
                    entity -> entity.setThrower(player.getUniqueId()));
            if (dropped != null) tuneDrop(dropped, config);
        }
        if (config.isHandSyncEnabled()) FakePlayerController.syncHeldItem(player);
    }

    private static void tuneDrop(Item dropped, BotConfig config) {
        if (config.getDropVelocityMultiplier() != 1.0) {
            dropped.setVelocity(dropped.getVelocity().multiply(config.getDropVelocityMultiplier()));
        }
        dropped.setPickupDelay(config.getDropPickupDelay());
    }

    /** Server-side pickup bridge with configurable rate, reach and inventory preference. */
    public static int pickupNearby(Bot bot, BotConfig config) {
        Player player = bot.getEntity();
        int processed = 0;
        int pickedTotal = 0;
        double radiusSquared = config.getPickupRadius() * config.getPickupRadius();

        for (Entity entity : player.getNearbyEntities(config.getPickupRadius(), config.getPickupRadius(), config.getPickupRadius())) {
            if (processed >= config.getPickupMaxItemsPerCheck()) break;
            if (!(entity instanceof Item item) || !item.isValid()) continue;

            double dx = item.getLocation().getX() - player.getLocation().getX();
            double dy = item.getLocation().getY() - player.getLocation().getY();
            double dz = item.getLocation().getZ() - player.getLocation().getZ();
            if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;

            if (config.isPickupRespectDelay() && (item.getPickupDelay() > 0 || !item.canPlayerPickup())) continue;
            if (config.isPickupRespectOwner()) {
                UUID owner = item.getOwner();
                if (owner != null && !owner.equals(player.getUniqueId())) continue;
            }

            ItemStack ground = item.getItemStack();
            if (ground.isEmpty()) continue;

            EntityPickupItemEvent event = new EntityPickupItemEvent(player, item, 0);
            if (!event.callEvent() || event.isCancelled()) continue;

            Map<Integer, ItemStack> leftover = addLikeNormalPlayer(player.getInventory(), ground.clone(), config);
            int remaining = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            int picked = ground.getAmount() - remaining;
            if (picked <= 0) continue;

            processed++;
            pickedTotal += picked;
            if (remaining <= 0) {
                item.remove();
            } else {
                ItemStack newGround = ground.clone();
                newGround.setAmount(remaining);
                item.setItemStack(newGround);
            }

            if (config.isHandSyncEnabled() && config.isHandSyncOnPickup()) {
                FakePlayerController.syncHeldItem(player);
            }
        }
        return pickedTotal;
    }

    /**
     * Configurable inventory insertion. Existing stacks are merged first; then,
     * depending on configuration, empty hotbar slots or normal storage slots are used.
     */
    private static Map<Integer, ItemStack> addLikeNormalPlayer(PlayerInventory inv, ItemStack incoming, BotConfig config) {
        Map<Integer, ItemStack> leftovers = new HashMap<>();
        int amount = incoming.getAmount();
        int storageLength = inv.getStorageContents().length;

        // Always merge into compatible existing stacks first.
        for (int slot = 0; slot < storageLength && amount > 0; slot++) {
            ItemStack current = inv.getItem(slot);
            if (current == null || current.isEmpty() || !current.isSimilar(incoming)) continue;
            int space = current.getMaxStackSize() - current.getAmount();
            if (space <= 0) continue;
            int moved = Math.min(space, amount);
            current.setAmount(current.getAmount() + moved);
            inv.setItem(slot, current);
            amount -= moved;
        }

        if (config.isPickupPreferHotbar()) {
            int preferred = config.getPickupPreferredHotbarSlot();
            if (preferred >= 0 && amount > 0 && isEmpty(inv.getItem(preferred))) {
                int moved = Math.min(incoming.getMaxStackSize(), amount);
                ItemStack placed = incoming.clone();
                placed.setAmount(moved);
                inv.setItem(preferred, placed);
                amount -= moved;
            }
            for (int slot = 0; slot <= 8 && amount > 0; slot++) {
                if (!isEmpty(inv.getItem(slot))) continue;
                int moved = Math.min(incoming.getMaxStackSize(), amount);
                ItemStack placed = incoming.clone();
                placed.setAmount(moved);
                inv.setItem(slot, placed);
                amount -= moved;
            }
            for (int slot = 9; slot < storageLength && amount > 0; slot++) {
                if (!isEmpty(inv.getItem(slot))) continue;
                int moved = Math.min(incoming.getMaxStackSize(), amount);
                ItemStack placed = incoming.clone();
                placed.setAmount(moved);
                inv.setItem(slot, placed);
                amount -= moved;
            }
        } else {
            for (int slot = 9; slot < storageLength && amount > 0; slot++) {
                if (!isEmpty(inv.getItem(slot))) continue;
                int moved = Math.min(incoming.getMaxStackSize(), amount);
                ItemStack placed = incoming.clone();
                placed.setAmount(moved);
                inv.setItem(slot, placed);
                amount -= moved;
            }
            for (int slot = 0; slot <= 8 && amount > 0; slot++) {
                if (!isEmpty(inv.getItem(slot))) continue;
                int moved = Math.min(incoming.getMaxStackSize(), amount);
                ItemStack placed = incoming.clone();
                placed.setAmount(moved);
                inv.setItem(slot, placed);
                amount -= moved;
            }
        }

        if (amount > 0) {
            ItemStack leftover = incoming.clone();
            leftover.setAmount(amount);
            leftovers.put(0, leftover);
        }
        return leftovers;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }
}
