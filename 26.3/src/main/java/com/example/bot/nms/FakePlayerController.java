package com.example.bot.nms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.item.component.SwingAnimation;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Entity;
import org.bukkit.Bukkit;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.util.Vector;

/** The version-sensitive operations that have no clean Bukkit equivalent for a fake player. */
public final class FakePlayerController {
    private FakePlayerController() {}

    private static ServerPlayer handle(org.bukkit.entity.Player bukkitPlayer) {
        return ((CraftPlayer) bukkitPlayer).getHandle();
    }

    /**
     * Find the entity a fake player's crosshair is actually over. The Bukkit
     * Player#getTargetEntity path is not used here because the fake player has
     * no client-side crosshair/raycast state to maintain.
     */
    public static org.bukkit.entity.Entity findAttackTarget(org.bukkit.entity.Player bukkitPlayer, double reach) {
        if (bukkitPlayer == null || !bukkitPlayer.isValid()) return null;

        double maxDistance = Math.max(1.0, Math.min(6.0, reach));
        org.bukkit.Location eye = bukkitPlayer.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection();

        org.bukkit.util.RayTraceResult entityHit = bukkitPlayer.getWorld().rayTraceEntities(
                eye, direction, maxDistance, 0.12,
                entity -> entity != bukkitPlayer && entity.isValid() && !entity.isDead()
                        && entity instanceof org.bukkit.entity.LivingEntity);
        org.bukkit.util.RayTraceResult blockHit = bukkitPlayer.getWorld().rayTraceBlocks(
                eye, direction, maxDistance, org.bukkit.FluidCollisionMode.NEVER, true);

        if (entityHit == null || entityHit.getHitEntity() == null) return null;
        if (blockHit == null || blockHit.getHitPosition() == null || entityHit.getHitPosition() == null) {
            return entityHit.getHitEntity();
        }

        double entityDistance = eye.toVector().distanceSquared(entityHit.getHitPosition());
        double blockDistance = eye.toVector().distanceSquared(blockHit.getHitPosition());
        return entityDistance <= blockDistance ? entityHit.getHitEntity() : null;
    }

    /**
     * Runs the real ServerPlayer melee attack path, including vanilla damage,
     * attack cooldowns, enchantments, combat events and knockback.
     */
    public static boolean attack(org.bukkit.entity.Player bukkitPlayer, org.bukkit.entity.Entity bukkitTarget, double reach) {
        if (bukkitPlayer == null || bukkitTarget == null || bukkitTarget == bukkitPlayer) return false;
        if (!(bukkitTarget instanceof org.bukkit.entity.LivingEntity) || !bukkitTarget.isValid()) return false;

        ServerPlayer attacker = handle(bukkitPlayer);
        net.minecraft.world.entity.Entity target =
                ((org.bukkit.craftbukkit.entity.CraftEntity) bukkitTarget).getHandle();

        // The target was already selected by a server-side ray trace, but keep
        // the NMS reach/interaction guard too, matching the normal player path.
        if (!attacker.isWithinEntityInteractionRange(target, reach)) return false;

        attacker.attack(target);
        attacker.swingAndResetAttackStrength(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        attacker.resetLastActionTime();
        return true;
    }

    /**
     * Performs a real right-click attempt: entity interaction first when the
     * entity is closer than the block, then block use/placement, then air use.
     * Both hands are attempted in vanilla order when the first hand passes.
     */
    public static boolean useMainHand(
            org.bukkit.entity.Player bukkitPlayer,
            double reach,
            boolean allowBlockItems,
            boolean allowNonBlockItems,
            boolean swingHand,
            boolean syncHand) {
        if (bukkitPlayer == null || !bukkitPlayer.isValid() || bukkitPlayer.isDead()) return false;

        ServerPlayer player = handle(bukkitPlayer);
        if (player.isUsingItem()) return true;

        double maxDistance = Math.max(1.0, Math.min(6.0, reach));
        org.bukkit.Location eye = bukkitPlayer.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection();
        org.bukkit.util.RayTraceResult entityHit = bukkitPlayer.getWorld().rayTraceEntities(
                eye, direction, maxDistance, 0.12,
                entity -> entity != bukkitPlayer && entity.isValid() && !entity.isDead());
        org.bukkit.util.RayTraceResult blockHit = bukkitPlayer.getWorld().rayTraceBlocks(
                eye, direction, maxDistance, org.bukkit.FluidCollisionMode.NEVER, true);

        boolean entityFirst = entityHit != null && entityHit.getHitEntity() != null
                && (blockHit == null || entityHit.getHitPosition() == null || blockHit.getHitPosition() == null
                || eye.toVector().distanceSquared(entityHit.getHitPosition())
                <= eye.toVector().distanceSquared(blockHit.getHitPosition()));

        if (entityFirst) {
            net.minecraft.world.entity.Entity target = ((org.bukkit.craftbukkit.entity.CraftEntity)
                    entityHit.getHitEntity()).getHandle();
            if (player.isWithinEntityInteractionRange(target, reach)) {
                net.minecraft.world.InteractionHand[] hands = {
                        InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND};
                for (InteractionHand hand : hands) {
                    org.bukkit.inventory.ItemStack bukkitStack = hand == InteractionHand.MAIN_HAND
                            ? bukkitPlayer.getInventory().getItemInMainHand()
                            : bukkitPlayer.getInventory().getItemInOffHand();
                    if (!isAllowedUseStack(bukkitStack, allowBlockItems, allowNonBlockItems)) continue;

                    net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
                    Vec3 hit = entityHit.getHitPosition() == null
                            ? new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ())
                            : new Vec3(entityHit.getHitPosition().getX(), entityHit.getHitPosition().getY(), entityHit.getHitPosition().getZ());
                    Vec3 relative = hit.subtract(target.getX(), target.getY(), target.getZ());

                    if (target.interact(player, hand, relative).consumesAction()) {
                        if (swingHand) player.swing(hand, SwingAnimation.DEFAULT, true);
                        if (syncHand) syncHeldItem(bukkitPlayer);
                        player.resetLastActionTime();
                        return true;
                    }

                    net.minecraft.world.InteractionResult result = player.interactOn(target, hand, relative);
                    if (result.consumesAction()) {
                        if (swingHand) player.swing(hand, SwingAnimation.DEFAULT, true);
                        if (syncHand) syncHeldItem(bukkitPlayer);
                        player.resetLastActionTime();
                        return true;
                    }
                }
            }
        }

        if (blockHit != null && blockHit.getHitBlock() != null && blockHit.getHitBlockFace() != null) {
            org.bukkit.block.Block block = blockHit.getHitBlock();
            org.bukkit.block.BlockFace face = blockHit.getHitBlockFace();
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(block.getX(), block.getY(), block.getZ());
            net.minecraft.core.Direction directionFace = net.minecraft.core.Direction.valueOf(face.name());
            Vec3 hitLocation = blockHit.getHitPosition() == null
                    ? new Vec3(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5)
                    : new Vec3(blockHit.getHitPosition().getX(), blockHit.getHitPosition().getY(), blockHit.getHitPosition().getZ());
            net.minecraft.world.phys.BlockHitResult hit =
                    new net.minecraft.world.phys.BlockHitResult(hitLocation, directionFace, pos, false);

            for (InteractionHand hand : new InteractionHand[]{InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND}) {
                org.bukkit.inventory.ItemStack bukkitStack = hand == InteractionHand.MAIN_HAND
                        ? bukkitPlayer.getInventory().getItemInMainHand()
                        : bukkitPlayer.getInventory().getItemInOffHand();
                if (!isAllowedUseStack(bukkitStack, allowBlockItems, allowNonBlockItems)) continue;

                net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
                net.minecraft.world.InteractionResult result = player.gameMode.useItemOn(
                        player, player.level(), stack, hand, hit);
                if (result.consumesAction()) {
                    if (swingHand) player.swing(hand, SwingAnimation.DEFAULT, true);
                    if (syncHand) syncHeldItem(bukkitPlayer);
                    player.resetLastActionTime();
                    return true;
                }
            }
        }

        // Empty-hand interactions (doors, buttons, trapdoors, etc.) are valid
        // too, so an empty main hand must not make /bot use fail outright.
        for (InteractionHand hand : new InteractionHand[]{InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND}) {
            org.bukkit.inventory.ItemStack bukkitStack = hand == InteractionHand.MAIN_HAND
                    ? bukkitPlayer.getInventory().getItemInMainHand()
                    : bukkitPlayer.getInventory().getItemInOffHand();
            if (!isAllowedUseStack(bukkitStack, allowBlockItems, allowNonBlockItems)) continue;

            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            net.minecraft.world.InteractionResult result = player.gameMode.useItem(
                    player, player.level(), stack, hand);
            if (result.consumesAction()) {
                if (swingHand) player.swing(hand, SwingAnimation.DEFAULT, true);
                if (syncHand) syncHeldItem(bukkitPlayer);
                player.resetLastActionTime();
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedUseStack(org.bukkit.inventory.ItemStack stack,
                                              boolean allowBlockItems,
                                              boolean allowNonBlockItems) {
        if (stack == null || stack.getType().isAir()) return true;
        return stack.getType().isBlock() ? allowBlockItems : allowNonBlockItems;
    }

    public static void releaseUsingItem(org.bukkit.entity.Player bukkitPlayer) {
        if (bukkitPlayer == null || !bukkitPlayer.isValid()) return;
        handle(bukkitPlayer).releaseUsingItem();
    }

    /** Forces the fake player's selected hotbar item into the entity-equipment state
     * so other real clients see it in the bot's hand, not merely in its inventory. */
    public static void syncHeldItem(org.bukkit.entity.Player bukkitPlayer) {
        ServerPlayer p = handle(bukkitPlayer);
        org.bukkit.inventory.ItemStack main = bukkitPlayer.getInventory().getItemInMainHand().clone();
        org.bukkit.inventory.ItemStack off = bukkitPlayer.getInventory().getItemInOffHand().clone();

        // Keep the inventory slot, entity equipment state, and what other
        // clients see in lock-step. Paper's equipment API broadcasts normal
        // equipment changes, while the NMS assignment keeps the ServerPlayer
        // state authoritative for subsequent use/interaction calls.
        bukkitPlayer.getEquipment().setItemInMainHand(main, false);
        bukkitPlayer.getEquipment().setItemInOffHand(off, false);
        p.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem().copy());
        p.setItemSlot(EquipmentSlot.OFFHAND, p.getOffhandItem().copy());

        java.util.Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> visible =
                java.util.Map.of(org.bukkit.inventory.EquipmentSlot.HAND, main,
                                 org.bukkit.inventory.EquipmentSlot.OFF_HAND, off);
        for (org.bukkit.entity.Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == bukkitPlayer) continue;
            try {
                viewer.sendEquipmentChange(bukkitPlayer, visible);
            } catch (Throwable ignored) {
                // The normal tracker packet path remains a fallback.
            }
        }
        markVelocitySync(p);
    }

    /** Applies a server-side velocity impulse using Bukkit's normal player path. */
    public static void applyKnockback(org.bukkit.entity.Player bukkitPlayer, Vector velocity) {
        bukkitPlayer.setVelocity(velocity.clone());
        ServerPlayer p = handle(bukkitPlayer);
        p.setOnGround(false);
        markVelocitySync(p);
    }

    /** Sends only the authoritative velocity to every real viewer of the bot. */
    public static void broadcastMotion(org.bukkit.entity.Player bukkitPlayer) {
        broadcastMotion(handle(bukkitPlayer));
    }

    public static void broadcastMotion(ServerPlayer bot) {
        ClientboundSetEntityMotionPacket motionPacket = new ClientboundSetEntityMotionPacket(
                bot.getId(), bot.getDeltaMovement());
        for (org.bukkit.entity.Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == bot.getBukkitEntity()) continue;
            try {
                ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle();
                viewerHandle.connection.send(motionPacket);
            } catch (Throwable ignored) {
                // Normal entity tracking remains the fallback.
            }
        }
    }

    /** Sends authoritative position AND velocity to every real viewer of the bot. */
    public static void broadcastPhysicsSnapshot(ServerPlayer bot) {
        ClientboundEntityPositionSyncPacket positionPacket = ClientboundEntityPositionSyncPacket.of(bot);
        ClientboundSetEntityMotionPacket motionPacket = new ClientboundSetEntityMotionPacket(
                bot.getId(), bot.getDeltaMovement());
        for (org.bukkit.entity.Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == bot.getBukkitEntity()) continue;
            try {
                ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle();
                viewerHandle.connection.send(positionPacket);
                viewerHandle.connection.send(motionPacket);
            } catch (Throwable ignored) {
                // Tracker packets remain the fallback.
            }
        }
    }

    public static void broadcastPhysicsSnapshot(org.bukkit.entity.Player bukkitPlayer) {
        broadcastPhysicsSnapshot(handle(bukkitPlayer));
    }

    static boolean isVelocitySyncMarked(ServerPlayer player) {
        java.lang.reflect.Field field = findField(player, "syncVelocity", "hurtMarked");
        if (field == null) return false;
        try {
            field.setAccessible(true);
            return field.getBoolean(player);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static void setVelocitySyncMarked(ServerPlayer player, boolean value) {
        java.lang.reflect.Field field = findField(player, "syncVelocity", "hurtMarked");
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.setBoolean(player, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void markVelocitySync(ServerPlayer player) {
        setVelocitySyncMarked(player, true);
    }

    private static java.lang.reflect.Field findField(Object instance, String... names) {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    return type.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return null;
    }

    public static void setLook(org.bukkit.entity.Player bukkitPlayer, float yaw, float pitch) {
        ServerPlayer p = handle(bukkitPlayer);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yHeadRot = yaw;
        p.yBodyRot = yaw;
        markVelocitySync(p);
    }

    public static void setSneaking(org.bukkit.entity.Player bukkitPlayer, boolean sneaking, boolean forcePose, boolean fixedPose) {
        ServerPlayer p = handle(bukkitPlayer);
        bukkitPlayer.setSneaking(sneaking);
        p.setShiftKeyDown(sneaking);
        if (forcePose) {
            bukkitPlayer.setPose(sneaking ? Pose.SNEAKING : Pose.STANDING, fixedPose);
            p.refreshDimensions();
        }
        markVelocitySync(p);
    }

}
