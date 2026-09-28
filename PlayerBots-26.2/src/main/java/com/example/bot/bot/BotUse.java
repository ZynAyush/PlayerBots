package com.example.bot.bot;

import com.example.bot.config.BotConfig;
import com.example.bot.nms.FakePlayerController;
import org.bukkit.entity.Player;

/** Server-side equivalent of a real player's right-click/use input. */
public final class BotUse {
    private BotUse() {}

    public static boolean useOnce(Bot bot, BotConfig config) {
        if (!config.isUseEnabled()) return false;
        Player player = bot.getEntity();
        if (player == null || !player.isValid() || player.isDead()) return false;

        float exhaustion = player.getExhaustion();

        // Fake players have no client to perform the normal crosshair ray trace.
        // Rebuild the same decision server-side and then enter the real
        // ServerPlayer interaction methods. This covers entity interaction,
        // block interaction/placement, and right-click-in-air item use.
        boolean used = FakePlayerController.useMainHand(
                player,
                config.getUseReach(),
                config.isUseAllowBlockItems(),
                config.isUseAllowNonBlockItems(),
                config.isUseSwingHand(),
                config.isHandSyncEnabled());
        if (used && !config.isUseDecreaseHunger()) {
            player.setExhaustion(exhaustion);
        }
        return used;
    }

    /** Release a held use action when /bot use stop is issued. */
    public static void stop(Bot bot) {
        if (bot == null || bot.getEntity() == null) return;
        FakePlayerController.releaseUsingItem(bot.getEntity());
    }
}
