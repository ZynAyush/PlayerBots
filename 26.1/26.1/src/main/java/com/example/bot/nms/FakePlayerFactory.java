package com.example.bot.nms;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/** Creates the fake ServerPlayer and the connection used by PlayerBots. */
public final class FakePlayerFactory {

    private static volatile boolean physicsEnabled = true;
    private static volatile boolean authMeBypassEnabled = true;

    /** Enables or disables the AuthMe NPC marker. */
    public static void setAuthMeBypassEnabled(boolean enabled) {
        authMeBypassEnabled = enabled;
    }

    private FakePlayerFactory() {}

    /** Enables or disables the fake player's server physics tick. */
    public static void setPhysicsEnabled(boolean enabled) {
        physicsEnabled = enabled;
    }

    /**
     * Creates and fully registers a real server-side player entity.
     *
     * @param name       the bot's in-game/display name (also used as the
     *                   fake profile name - must be a legal player name,
     *                   <= 16 chars, so it also shows correctly above
     *                   the entity's head and in the tab list).
     * @param uuid       a stable UUID for this bot. Callers should
     *                   persist and re-use this across restarts for
     *                   persistent bots so tab-list/scoreboard identity
     *                   stays consistent.
     * @param skinOwner  optional: if non-null, the returned profile's
     *                   skin texture property is copied from this
     *                   already-online player (used by /bot skin).
     * @param spawnAt    the location to place the bot at.
     * @return the Bukkit-facing Player object for the new bot. The
     *         caller (FakePlayerController) is responsible for further
     *         per-bot bookkeeping.
     */
    public static Player createFakePlayer(String name, UUID uuid, Player skinOwner, Location spawnAt) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        GameProfile profile;
        if (skinOwner != null) {
            GameProfile ownerProfile = ((CraftPlayer) skinOwner).getProfile();
            PropertyMap copiedProperties = new PropertyMap(ownerProfile.properties());
            profile = new GameProfile(uuid, name, copiedProperties);
        } else {
            profile = new GameProfile(uuid, name, new PropertyMap(ImmutableMultimap.of()));
        }

        net.minecraft.server.level.ServerLevel level =
                ((CraftWorld) spawnAt.getWorld()).getHandle();

        BotServerPlayer nmsPlayer = new BotServerPlayer(server, level, profile, ClientInformation.createDefault());

        nmsPlayer.setPos(spawnAt.getX(), spawnAt.getY(), spawnAt.getZ());
        nmsPlayer.setYRot(spawnAt.getYaw());
        nmsPlayer.setXRot(spawnAt.getPitch());

        DummyConnection connection = new DummyConnection(PacketFlow.SERVERBOUND);

        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ServerGamePacketListenerImpl gameListener =
                new ServerGamePacketListenerImpl(server, connection, nmsPlayer, cookie);
        connection.setPacketListenerForFakePlayer(gameListener);

        // AuthMeReloaded explicitly recognizes players carrying the Bukkit
        // "NPC" metadata as NPCs and skips its authentication restrictions.
        // Set this BEFORE placeNewPlayer(), because that method fires the
        // PlayerJoinEvent during the synthetic login sequence.
        if (authMeBypassEnabled) {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerBots");
            if (plugin != null && plugin.isEnabled()) {
                nmsPlayer.getBukkitEntity().setMetadata("NPC", new FixedMetadataValue(plugin, true));
            }
        }

        // This is the call that does the "real login" work: adds the
        // entity to the level, notifies the entity tracker so nearby
        // real players start receiving spawn/movement packets for it,
        // adds it to the tab list, assigns it a client-bound entity id,
        // fires PlayerJoinEvent, etc. - all the things that make this a
        // genuine player rather than a lookalike.
        server.getPlayerList().placeNewPlayer(connection, nmsPlayer, cookie);

        // placeNewPlayer normally teleports to the world's spawn point
        // (as if freshly logging in); force it back to the requested
        // location afterwards.
        nmsPlayer.teleportTo(level, spawnAt.getX(), spawnAt.getY(), spawnAt.getZ(),
                Set.of(), spawnAt.getYaw(), spawnAt.getPitch(), false);

        // A bot has no real client, so mark it "invulnerable-to-timeout"
        // in our own bookkeeping rather than trying to spoof keepalive
        // packets; DummyConnection.tick() is overridden to never expire it.

        return nmsPlayer.getBukkitEntity();
    }

    /** Removes a fake player from the server. */
    public static void removeFakePlayer(Player bukkitPlayer) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
        try {
            // remove() with an explicit reason fires the same disconnect/
            // cleanup path a real client quitting would: removed from
            // tab list, entity tracker tells nearby clients to destroy
            // the entity, inventory/ender-chest not touched, etc.
            server.getPlayerList().remove(nmsPlayer);
        } catch (Throwable t) {
            // Fallback if the PlayerList cleanup path itself cannot run.
        } finally {
            // PlayerList.remove() normally handles this, but a dead synthetic
            // ServerPlayer can otherwise remain in the level/entity tracker
            // long enough to leave a corpse/nametag visible. discard() is
            // idempotent once the entity has already been removed.
            try {
                nmsPlayer.discard();
            } catch (Throwable ignored) {
                // Nothing else is safe to do here.
            }
        }
    }


    /**
     * Real fake players need the normal ServerPlayer#doTick() path because they have
     * no client to drive it. Calling doTick() is what gives the entity vanilla
     * gravity, collision, velocity integration, fall damage, knockback movement,
     * and the rest of the ordinary server-side player/entity physics.
     */
    private static final class BotServerPlayer extends ServerPlayer {
        private int trackingTickCounter;

        private BotServerPlayer(MinecraftServer server, net.minecraft.server.level.ServerLevel level,
                                GameProfile profile, ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            boolean damaged = super.hurtServer(level, source, amount);
            if (!damaged || !FakePlayerController.isVelocitySyncMarked(this)) return damaged;

            FakePlayerController.setVelocitySyncMarked(this, false);
            var plugin = Bukkit.getPluginManager().getPlugin("PlayerBots");
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isAlive() && !isRemoved()) {
                        FakePlayerController.setVelocitySyncMarked(this, true);
                    }
                });
            } else {
                FakePlayerController.setVelocitySyncMarked(this, true);
            }
            return damaged;
        }

        @Override
        public void tick() {
            // Keep chunk tracking and the fake connection's logical position fresh,
            // but never call Connection.tick(), which would expect a real client.
            if (++trackingTickCounter >= 10) {
                trackingTickCounter = 0;
                try {
                    this.connection.resetPosition();
                } catch (Throwable ignored) {
                }
                try {
                    this.level().getChunkSource().move(this);
                } catch (Throwable ignored) {
                }
            }

            // ServerPlayer.tick() handles the server-side player bookkeeping.
            // doTick() contains the actual LivingEntity/Entity tick path.
            super.tick();
            if (physicsEnabled) {
                this.doTick();
            }
        }
    }

    /**
     * A {@link Connection} with no real network channel behind it. All
     * outbound packets are simply dropped instead of being written to a
     * socket, and it never times out. This is what lets
     * PlayerList#placeNewPlayer() succeed without throwing NPEs on a
     * null channel, and stops the server's connection-timeout watchdog
     * from disconnecting a bot that never sends a keep-alive response.
     *
     */
    private static final class DummyConnection extends Connection {

        private DummyConnection(PacketFlow flow) {
            super(flow);
            // Connection.channel/address are private in modern mappings,
            // so set them reflectively rather than accessing private fields
            // directly from the subclass.
            EmbeddedChannel embedded = new EmbeddedChannel();
            setPrivateField(Connection.class, this, "channel", embedded);
            setPrivateField(Connection.class, this, "address", embedded.localAddress());
        }

        private void setPacketListenerForFakePlayer(PacketListener listener) {
            setPrivateField(Connection.class, this, "packetListener", listener);
        }

        private static void setPrivateField(Class<?> owner, Object target, String fieldName, Object value) {
            try {
                var field = owner.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to initialize fake connection field '" + fieldName + "'", e);
            }
        }

        @Override
        public void send(Packet<?> packet) {
            // Drop silently - nobody is listening on the other end.
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener listener) {
            // Outbound packets are intentionally discarded. There is no
            // client socket to notify, so callbacks are ignored as well.
        }

        @Override
        public void tick() {
            // Deliberately skip super.tick(): that is where the base
            // Connection measures time-since-last-keepalive and would
            // otherwise disconnect us for "timing out".
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }
}
