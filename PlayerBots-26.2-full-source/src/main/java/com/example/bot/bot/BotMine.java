package com.example.bot.bot;

import com.example.bot.config.BotConfig;
import com.example.bot.nms.FakePlayerController;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.FluidCollisionMode;

/** Server-side block mining with real Bukkit protection/drop/durability hooks. */
public final class BotMine {
    private BotMine() {}

    public static boolean mineOnce(Bot bot, BotConfig config) {
        if (bot == null || config == null || !config.isMineEnabled()) return false;
        Player player = bot.getEntity();
        if (player == null || !player.isValid() || player.isDead()) return false;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;

        double reach = config.getMineReach();
        org.bukkit.Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), reach, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            bot.clearMiningState();
            return false;
        }

        Block block = hit.getHitBlock();
        if (block.getType().isAir() || block.isLiquid()) {
            bot.clearMiningState();
            return false;
        }

        String target = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (!target.equals(bot.getMiningTarget())) bot.setMiningState(target, 0.0f);

        float speed = block.getBreakSpeed(player);
        if (!(speed > 0.0f) || Float.isNaN(speed) || Float.isInfinite(speed)) {
            return false;
        }

        float progress = bot.getMiningProgress() + speed;
        if (progress < 1.0f) {
            bot.setMiningState(target, progress);
            return true;
        }

        boolean broken = player.breakBlock(block);
        bot.clearMiningState();
        if (broken && config.isHandSyncEnabled()) {
            FakePlayerController.syncHeldItem(player);
        }
        return broken;
    }
}
