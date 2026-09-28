package com.example.bot.bot;

import com.example.bot.nms.FakePlayerController;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Server-side equivalent of a real player's left-click melee attack. */
public final class BotAttack {
    private BotAttack() {}

    public static boolean attackOnce(Bot bot, double reach) {
        Player player = bot.getEntity();
        if (player == null || !player.isValid() || player.isDead()) return false;

        Entity target = FakePlayerController.findAttackTarget(player, reach);
        if (target == null || target == player || !target.isValid()) return false;

        // This intentionally enters the NMS ServerPlayer attack path instead
        // of applying damage ourselves. That preserves vanilla combat logic,
        // cooldown handling, enchantments, events and knockback.
        return FakePlayerController.attack(player, target, reach);
    }
}
