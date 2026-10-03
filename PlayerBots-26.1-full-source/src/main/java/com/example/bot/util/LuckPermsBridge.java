package com.example.bot.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Optional LuckPerms integration used only to make the bot display prefix deterministic. */
public final class LuckPermsBridge {
    private static final int BOT_PREFIX_PRIORITY = Integer.MAX_VALUE;
    private LuckPermsBridge() {}

    public static void applyBotPrefix(Plugin plugin, UUID uuid, String username, boolean enabled, String prefix) {
        if (!enabled || plugin == null || uuid == null || username == null) return;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) applyDirectPrefix(online, prefix);
        Plugin lp = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (lp == null || !lp.isEnabled()) return;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
            if (user != null) {
                setPrefix(user, userManager, prefix);
                return;
            }
            Method load = findLoadUser(userManager);
            if (load == null) return;
            Object stage = load.getParameterCount() == 2 ? load.invoke(userManager, uuid, username) : load.invoke(userManager, uuid);
            if (stage instanceof CompletionStage<?> completion) {
                completion.thenAccept(loaded -> {
                    if (loaded == null) return;
                    try {
                        setPrefix(loaded, userManager, prefix);
                        Player loadedOnline = Bukkit.getPlayer(uuid);
                        if (loadedOnline != null) applyDirectPrefix(loadedOnline, prefix);
                    } catch (ReflectiveOperationException ignored) {}
                });
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // LuckPerms remains optional.
        }
    }

    private static void applyDirectPrefix(Player player, String prefix) {
        if (player == null) return;
        String value = prefix == null ? "" : prefix;
        String display = org.bukkit.ChatColor.translateAlternateColorCodes('&', value) + player.getName();
        player.setDisplayName(display);
        player.setPlayerListName(display);
    }

    private static Method findLoadUser(Object userManager) {
        for (Method m : userManager.getClass().getMethods()) {
            if (!m.getName().equals("loadUser")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == UUID.class && p[1] == String.class) return m;
            if (p.length == 1 && p[0] == UUID.class) return m;
        }
        return null;
    }

    private static void setPrefix(Object user, Object userManager, String prefix) throws ReflectiveOperationException {
        Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
        Object node = nodeClass.getMethod("builder", String.class)
                .invoke(null, "prefix." + BOT_PREFIX_PRIORITY + "." + (prefix == null ? "" : prefix))
                ;
        node = node.getClass().getMethod("build").invoke(node);

        Method getNodes = findMethod(user.getClass(), "getNodes", 0);
        Method getData = findMethod(user.getClass(), "data", 0);
        if (getData == null) return;
        Object data = getData.invoke(user);
        Method remove = findSingleArgMethod(data.getClass(), "remove");
        Method add = findSingleArgMethod(data.getClass(), "add");

        if (getNodes != null && remove != null) {
            Object nodes = getNodes.invoke(user);
            if (nodes instanceof Iterable<?> iterable) {
                for (Object existing : iterable) {
                    try {
                        Method key = findMethod(existing.getClass(), "getKey", 0);
                        if (key == null) continue;
                        Object k = key.invoke(existing);
                        if (k instanceof String text && text.startsWith("prefix." + BOT_PREFIX_PRIORITY + ".")) {
                            remove.invoke(data, existing);
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            }
        }
        if (add != null) {
            add.invoke(data, node);
            Method save = findSingleArgMethod(userManager.getClass(), "saveUser");
            if (save != null) save.invoke(userManager, user);
        }
    }

    private static Method findMethod(Class<?> type, String name, int params) {
        for (Method m : type.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == params) return m;
        return null;
    }

    private static Method findSingleArgMethod(Class<?> type, String name) {
        for (Method m : type.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == 1) return m;
        return null;
    }
}
