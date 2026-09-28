package com.example.bot.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Small optional LuckPerms bridge. No hard dependency on the LuckPerms API. */
public final class LuckPermsBridge {
    private static final String PREFIX_NODE = "prefix.2000000000.&r";

    private LuckPermsBridge() {
    }

    public static void hidePrefix(Plugin plugin, UUID uuid, String username, boolean enabled) {
        if (!enabled || plugin == null || uuid == null || username == null) return;

        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) return;

        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, uuid);

            if (user != null) {
                addPrefix(user, userManager);
                return;
            }

            Method loadUser = findLoadUser(userManager);
            if (loadUser == null) return;
            Object stageObject;
            if (loadUser.getParameterCount() == 2) {
                stageObject = loadUser.invoke(userManager, uuid, username);
            } else {
                stageObject = loadUser.invoke(userManager, uuid);
            }
            if (stageObject instanceof CompletionStage<?> stage) {
                stage.thenAccept(loaded -> {
                    if (loaded == null) return;
                    try {
                        addPrefix(loaded, userManager);
                    } catch (ReflectiveOperationException ignored) {
                    }
                });
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // LuckPerms is optional. If its API changes, PlayerBots must still work.
        }
    }

    private static Method findLoadUser(Object userManager) {
        for (Method method : userManager.getClass().getMethods()) {
            if (!method.getName().equals("loadUser")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && params[0] == UUID.class && params[1] == String.class) return method;
            if (params.length == 1 && params[0] == UUID.class) return method;
        }
        return null;
    }

    private static void addPrefix(Object user, Object userManager) throws ReflectiveOperationException {
        Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
        Method builderMethod = nodeClass.getMethod("builder", String.class);
        Object builder = builderMethod.invoke(null, PREFIX_NODE);
        Object node = builder.getClass().getMethod("build").invoke(builder);

        Object userData = user.getClass().getMethod("data").invoke(user);
        Method add = findDataMethod(userData.getClass(), "add", nodeClass);
        Method remove = findDataMethod(userData.getClass(), "remove", nodeClass);
        if (add == null) return;

        // Keep exactly one PlayerBots-owned no-prefix node at high priority.
        if (remove != null) {
            try {
                remove.invoke(userData, node);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        add.invoke(userData, node);
        userManager.getClass().getMethod("saveUser", Class.forName("net.luckperms.api.model.user.User"))
                .invoke(userManager, user);
    }

    private static Method findDataMethod(Class<?> type, String name, Class<?> nodeClass) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(nodeClass)) {
                return method;
            }
        }
        return null;
    }
}
