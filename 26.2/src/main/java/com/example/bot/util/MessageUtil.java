package com.example.bot.util;

import com.example.bot.BotPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class MessageUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    public static void info(CommandSender to, String msg) {
        send(to, BotPlugin.getInstance().getBotConfig().getMessagePrefix()
                + BotPlugin.getInstance().getBotConfig().getMessageInfoColor() + msg);
    }

    public static void success(CommandSender to, String msg) {
        send(to, BotPlugin.getInstance().getBotConfig().getMessagePrefix()
                + BotPlugin.getInstance().getBotConfig().getMessageSuccessColor() + msg);
    }

    public static void error(CommandSender to, String msg) {
        send(to, BotPlugin.getInstance().getBotConfig().getMessagePrefix()
                + BotPlugin.getInstance().getBotConfig().getMessageErrorColor() + msg);
    }

    public static void warn(CommandSender to, String msg) {
        send(to, BotPlugin.getInstance().getBotConfig().getMessagePrefix()
                + BotPlugin.getInstance().getBotConfig().getMessageWarnColor() + msg);
    }

    private static void send(CommandSender to, String legacy) {
        Component component = LEGACY.deserialize(legacy);
        to.sendMessage(component);
    }
}
