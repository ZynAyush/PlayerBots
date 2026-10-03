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
        boolean used = FakePlayerController.useMainHand(
                player, config.getUseReach(), config.isUseAllowBlockItems(),
                config.isUseAllowNonBlockItems(), config.isUseSwingHand(), config.isHandSyncEnabled());

        if (!used && config.isUseMineFallback()) used = BotMine.mineOnce(bot, config);
        if (used && !config.isUseDecreaseHunger()) player.setExhaustion(exhaustion);
        return used;
    }

    public static void stop(Bot bot) {
        if (bot == null || bot.getEntity() == null) return;
        FakePlayerController.releaseUsingItem(bot.getEntity());
    }
}
