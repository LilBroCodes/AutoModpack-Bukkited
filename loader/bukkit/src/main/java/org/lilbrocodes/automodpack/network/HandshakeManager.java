package org.lilbrocodes.automodpack.network;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.lilbrocodes.automodpack.AutoModpack;
import pl.skidam.automodpack_core.auth.Secrets;
import pl.skidam.automodpack_core.auth.SecretsStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static java.lang.reflect.Modifier.isStatic;
import static pl.skidam.automodpack_core.GlobalVariables.*;

public class HandshakeManager {
    private final AutoModpack plugin;
    private final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
    private final Gson gson = new Gson();

    private static final String HANDSHAKE_CHANNEL = "automodpack:handshake";
    private static final String DATA_CHANNEL = "automodpack:data";
    private static final List<String> ACCEPTED_LOADERS = Arrays.asList("fabric", "forge", "neoforge");

    public HandshakeManager(AutoModpack plugin) {
        this.plugin = plugin;
        registerListeners();
    }

    private void registerListeners() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Login.Client.START) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (DEBUG) LOGGER.info("Client login detected: {}", getPlayerName(event));
                sendHandshakePacket(event);
            }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Login.Client.CUSTOM_PAYLOAD) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    Object serializer = getFieldOfType(event.getPacket().getHandle(), "net.minecraft.network.PacketDataSerializer");
                    if (serializer == null) {
                        plugin.getLogger().warning("Packet missing payload!");
                        return;
                    }
                    String json = (String) findMethod(serializer.getClass(), "e", int.class).invoke(serializer, Short.MAX_VALUE);
                    if (DEBUG) LOGGER.info("Received payload JSON: {}", json);
                    if ("false".equals(json)) {
                        voidEvent(event);
                        return;
                    }
                    handleHandshakeResponse(event, HandshakePacket.fromJson(json));
                } catch (Exception e) {
                    LOGGER.error("Error in login-query: {}", String.valueOf(e));
                    disconnect(event, "Invalid handshake response.");
                }
            }
        });
    }

    private void sendHandshakePacket(PacketEvent event) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Login.Server.CUSTOM_PAYLOAD);
            Object handle = packet.getHandle();
            Object channelKey = newInstance("net.minecraft.resources.MinecraftKey", HANDSHAKE_CHANNEL);
            Object serializer = createSerializer(gson.toJson(new HandshakePacket(
                    ACCEPTED_LOADERS, AutoModpack.PLUGIN_VERSION, plugin.getServer().getBukkitVersion().split("-")[0]
            )));
            setHandleFields(handle, channelKey, serializer);
            protocolManager.sendServerPacket(event.getPlayer(), packet);
            if (DEBUG) LOGGER.info("Sent handshake packet to {}", getPlayerName(event));
        } catch (Exception e) {
            LOGGER.error("Failed to send handshake: {}", String.valueOf(e));
        }
    }

    private String getPlayerName(PacketEvent event) {
        String playerName = null;
        try {
            playerName = (String) findField(event.getSource(), "playerName", String.class.getName());
        } catch (Exception ignored) {

        }
        return playerName == null || playerName.equals("protocol_lib_inbound_interceptor") ? "UNKNOWN" : playerName;
    }

    private void sendDataPacket(PacketEvent event) {
        try {
            voidEvent(event);
            if (!plugin.server.isRunning()) return;
            if (modpackExecutor.isGenerating()) {
                disconnect(event, "Modpack is generating. Please wait.");
                return;
            }
            String playerName = (String) getFieldOfType(event.getSource(), String.class.getName());
            if (playerName == null) {
                disconnect(event, "Failed to get player name!");
                return;
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            Secrets.Secret secret = Secrets.generateSecret();
            SecretsStore.saveHostSecret(player.getUniqueId().toString(), secret);

            DataPacket data = new DataPacket(serverConfig.addressToSend, serverConfig.portToSend, serverConfig.modpackName,
                    secret, serverConfig.requireAutoModpackOnClient, serverConfig.bindPort == -1);
            PacketContainer packet = protocolManager.createPacket(PacketType.Login.Server.CUSTOM_PAYLOAD);
            Object handle = packet.getHandle();
            Object channelKey = newInstance("net.minecraft.resources.MinecraftKey", DATA_CHANNEL);
            Object serializer = createSerializer(data.toJson());
            setHandleFields(handle, channelKey, serializer);
            protocolManager.sendServerPacket(event.getPlayer(), packet);
        } catch (Exception e) {
            LOGGER.error("Failed to send data packet: {}", String.valueOf(e));
        }
    }

    private void handleHandshakeResponse(PacketEvent event, HandshakePacket packet) {
        if (!AutoModpack.PLUGIN_VERSION.equals(packet.amVersion)) {
            disconnect(event, "Version mismatch! Server: " + AutoModpack.PLUGIN_VERSION + ", Client: " + packet.amVersion);
            return;
        }
        if (DEBUG) LOGGER.info("Handshake passed: {}", getPlayerName(event));
        sendDataPacket(event);
    }

    private void disconnect(PacketEvent event, String reason) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Login.Server.DISCONNECT);
            packet.getChatComponents().write(0, WrappedChatComponent.fromText(reason));
            protocolManager.sendServerPacket(event.getPlayer(), packet);
            plugin.getServer().getScheduler().runTask(plugin, () -> event.getPlayer().kickPlayer(reason));
            if (DEBUG) LOGGER.info("Kicked {}: {}", getPlayerName(event), reason);
        } catch (Exception e) {
            LOGGER.error("Failed to kick player: {}", String.valueOf(e));
        }
    }

    private void voidEvent(PacketEvent event) {
        event.setCancelled(true);
        event.setReadOnly(true);
    }

    private Object createSerializer(String json) throws Exception {
        Object serializer = newInstance("net.minecraft.network.PacketDataSerializer", Unpooled.buffer());
        findMethod(serializer.getClass(), "a", String.class).invoke(serializer, json);
        return serializer;
    }

    private void setHandleFields(Object handle, Object channelKey, Object serializer) throws Exception {
        for (Field field : handle.getClass().getDeclaredFields()) {
            if (isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType().isAssignableFrom(channelKey.getClass())) field.set(handle, channelKey);
            else if (field.getType().isAssignableFrom(serializer.getClass())) field.set(handle, serializer);
        }
    }

    private static Object findField(Object instance, String name, String typeName) throws Exception {
        for (Field field : instance.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType().getName().equals(typeName) && field.getName().equals(name)) return field.get(instance);
        }
        return null;
    }

    private static Object getFieldOfType(Object instance, String typeName) throws Exception {
        for (Field field : instance.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType().getName().equals(typeName)) return field.get(instance);
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) throws Exception {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), params)) return m;
        }
        throw new IllegalStateException("Method not found: " + name);
    }

    private static Object newInstance(String className, Object... args) throws Exception {
        Class<?> clazz = Class.forName(className);
        for (var c : clazz.getConstructors()) {
            if (c.getParameterCount() == args.length) return c.newInstance(args);
        }
        throw new IllegalStateException("Constructor not found: " + className);
    }

    private static class HandshakePacket {
        public List<String> loaders;
        public String amVersion;
        public String mcVersion;
        public HandshakePacket(List<String> loaders, String amVersion, String mcVersion) {
            this.loaders = loaders; this.amVersion = amVersion; this.mcVersion = mcVersion;
        }
        public static HandshakePacket fromJson(String json) {
            return new Gson().fromJson(json, HandshakePacket.class);
        }
    }

    public static class DataPacket {
        public String address;
        public int port;
        public String modpackName;
        public Secrets.Secret secret;
        public boolean modRequired;
        public boolean requiresMagic;
        public DataPacket(String address, int port, String modpackName, Secrets.Secret secret, boolean modRequired, boolean requiresMagic) {
            this.address = address; this.port = port; this.modpackName = modpackName; this.secret = secret;
            this.modRequired = modRequired; this.requiresMagic = requiresMagic;
        }
        public String toJson() {
            return new Gson().toJson(this);
        }
        public static DataPacket fromJson(String json) {
            return new Gson().fromJson(json, DataPacket.class);
        }
    }
}
