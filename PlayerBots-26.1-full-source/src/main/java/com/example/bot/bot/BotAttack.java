package com.example.bot.bot;

import com.example.bot.nms.FakePlayerController;
import com.example.bot.config.BotConfig;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Server-side equivalent of a real player's left-click melee attack. */
public final class BotAttack {
    private BotAttack() {}

    public static boolean attackOnce(Bot bot, double reach, BotConfig config) {
        Player player = bot.getEntity();
        if (player == null || !player.isValid() || player.isDead()) return false;

        Entity target = FakePlayerController.findAttackTarget(player, reach);
        if (target == null || target == player || !target.isValid()) return false;

        // Let the real ServerPlayer attack code handle damage, cooldown,
        // enchantments, events and knockback.
        float exhaustion = player.getExhaustion();
        boolean attacked = FakePlayerController.attack(player, target, reach);
        if (attacked && config != null && !config.isAttackDecreaseHunger()) {
            player.setExhaustion(exhaustion);
        }
        return attacked;
    }
}
