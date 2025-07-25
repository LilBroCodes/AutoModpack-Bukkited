package org.lilbrocodes.automodpack.network;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.injector.netty.channel.NettyChannelInjector;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.lilbrocodes.automodpack.AutoModpack;
import pl.skidam.automodpack_core.auth.Secrets;
import pl.skidam.automodpack_core.auth.SecretsStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static pl.skidam.automodpack_core.GlobalVariables.*;
import static pl.skidam.automodpack_core.GlobalVariables.serverConfig;

public class HandshakeManager {
    private final AutoModpack plugin;
    private final ProtocolManager protocolManager;
    private final Gson gson = new Gson();

    private static final String HANDSHAKE_CHANNEL = "automodpack:handshake";
    private static final String DATA_CHANNEL = "automodpack:data";
    private static final List<String> ACCEPTED_LOADERS = Arrays.asList("fabric", "forge", "neoforge");

    public HandshakeManager(AutoModpack plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        registerListeners();
    }

    private void registerListeners() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                PacketType.Login.Client.START) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                plugin.getLogger().info("Client login detected: " + event.getPlayer().getName());
                sendHandshakePacket(event);
            }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Login.Client.CUSTOM_PAYLOAD) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    Object handle = event.getPacket().getHandle();
                    Class<?> packetClass = handle.getClass();

                    Class<?> minecraftKeyClass = Class.forName("net.minecraft.resources.MinecraftKey");
                    Class<?> serializerClass = Class.forName("net.minecraft.network.PacketDataSerializer");

                    Object payloadSerializer = null;

                    for (Field field : packetClass.getDeclaredFields()) {
                        field.setAccessible(true);
                        Class<?> type = field.getType();
                        if (serializerClass.isAssignableFrom(type)) {
                            payloadSerializer = field.get(handle);
                        }
                    }

                    Method readUtf = null;
                    for (Method m : serializerClass.getMethods()) {
                        if (m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == int.class
                                && m.getName().equals("e")) {
                            readUtf = m;
                            break;
                        }
                    }

                    if (payloadSerializer == null) {
                        plugin.getLogger().warning("Packet missing channel or payload!");
                        return;
                    }

                    String json = (String) readUtf.invoke(payloadSerializer, Short.MAX_VALUE);

                    plugin.getLogger().info("Received payload JSON: " + json);

                    if (json.equals("false")) {
                        plugin.getLogger().info("Payload JSON is info packet");
                        voidEvent(event);
                        return;
                    }

                    HandshakePacket packet = HandshakePacket.fromJson(json);
                    handleHandshakeResponse(event, packet);

                } catch (Exception e) {
                    plugin.getLogger().severe("Error in login‐query listener: " + e);
                    e.printStackTrace();
                    disconnectPlayer(event.getPlayer(), "Invalid handshake response.");
                }
            }
        });

    }

    private void sendDataPacket(PacketEvent event) {
        try {
            voidEvent(event);
            PacketContainer packet = protocolManager.createPacket(PacketType.Login.Server.CUSTOM_PAYLOAD);

            if (!plugin.server.isRunning()) {
                LOGGER.info("Host server is not running. Modpack will not be sent to {}", event.getPlayer().getName());
                return;
            }

            if (modpackExecutor.isGenerating()) {
                disconnectPlayer(event.getPlayer(), "AutoModpack is generating modpack. Please wait a moment and try again.");
                return;
            }

            NettyChannelInjector source = (NettyChannelInjector) event.getSource();

            String playerName = null;
            for (Field field : source.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Class<?> type = field.getType();
                if (String.class.isAssignableFrom(type)) {
                    playerName = (String) field.get(source);
                }
            }
            if (playerName == null) {
                disconnectPlayer(event.getPlayer(), "Failed to get player name! This is a bug and shouldn't happen, please report it on the github!");
                return;
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            Secrets.Secret secret = Secrets.generateSecret();
            SecretsStore.saveHostSecret(player.getUniqueId().toString(), secret);

            String addressToSend = serverConfig.addressToSend;
            int portToSend = serverConfig.portToSend;
            boolean requiresMagic = serverConfig.bindPort == -1;


            LOGGER.info("Sending {} modpack host address: {}:{}", event.getPlayer().getName(), addressToSend, portToSend);
            DataPacket dataPacket = new DataPacket(addressToSend, portToSend, serverConfig.modpackName, secret, serverConfig.requireAutoModpackOnClient, requiresMagic);

            String packetContentJson = dataPacket.toJson();

            Object handle = packet.getHandle();

            Class<?> minecraftKeyClass = Class.forName("net.minecraft.resources.MinecraftKey");
            Object channelKey = minecraftKeyClass.getConstructor(String.class).newInstance(DATA_CHANNEL);

            ByteBuf byteBuf = Unpooled.buffer();
            Class<?> serializerClass = Class.forName("net.minecraft.network.PacketDataSerializer");
            Object serializer = serializerClass.getConstructor(ByteBuf.class).newInstance(byteBuf);

            Method writeUtf = null;
            for (Method m : serializerClass.getMethods()) {
                if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class
                        && m.getName().equals("a")) {
                    writeUtf = m;
                    break;
                }
            }
            if (writeUtf == null) throw new IllegalStateException("Could not find writeUtf method");

            writeUtf.invoke(serializer, packetContentJson);

            for (Field field : handle.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

                field.setAccessible(true);
                if (minecraftKeyClass.isAssignableFrom(field.getType())) {
                    field.set(handle, channelKey);
                } else if (serializerClass.isAssignableFrom(field.getType())) {
                    field.set(handle, serializer);
                }
            }

            protocolManager.sendServerPacket(event.getPlayer(), packet);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send data packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void voidEvent(PacketEvent event) {
        event.setCancelled(true);
        event.setReadOnly(true);
    }

    private void sendHandshakePacket(PacketEvent event) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Login.Server.CUSTOM_PAYLOAD);
            Object handle = packet.getHandle();

            Class<?> minecraftKeyClass = Class.forName("net.minecraft.resources.MinecraftKey");
            Object channelKey = minecraftKeyClass.getConstructor(String.class).newInstance(HANDSHAKE_CHANNEL);

            String json = gson.toJson(new HandshakePacket(
                    ACCEPTED_LOADERS,
                    AutoModpack.PLUGIN_VERSION,
                    plugin.getServer().getBukkitVersion().split("-")[0]
            ));

            ByteBuf byteBuf = Unpooled.buffer();
            Class<?> serializerClass = Class.forName("net.minecraft.network.PacketDataSerializer");
            Object serializer = serializerClass.getConstructor(ByteBuf.class).newInstance(byteBuf);

            Method writeUtf = null;
            for (Method m : serializerClass.getMethods()) {
                if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class
                        && m.getName().equals("a")) {
                    writeUtf = m;
                    break;
                }
            }
            if (writeUtf == null) throw new IllegalStateException("Could not find writeUtf method");

            writeUtf.invoke(serializer, json);

            for (Field field : handle.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

                if (minecraftKeyClass.isAssignableFrom(field.getType())) {
                    field.set(handle, channelKey);
                } else if (serializerClass.isAssignableFrom(field.getType())) {
                    field.set(handle, serializer);
                }
            }

            protocolManager.sendServerPacket(event.getPlayer(), packet);
            plugin.getLogger().info("Sent handshake packet to " + event.getPlayer().getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send handshake: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleHandshakeResponse(PacketEvent event, HandshakePacket readPacket) {
        try {
            String clientVersion = readPacket.amVersion;

            if (!AutoModpack.PLUGIN_VERSION.equals(clientVersion)) {
                String reason = "AutoModpack version mismatch! Server: " + AutoModpack.PLUGIN_VERSION +
                        ", Client: " + clientVersion;
                disconnectPlayer(event.getPlayer(), reason);
                return;
            }

            plugin.getLogger().info("Handshake passed from " + event.getPlayer().getName());
            sendDataPacket(event);

        } catch (Exception e) {
            plugin.getLogger().severe("Error parsing handshake response: " + e.getMessage());
            e.printStackTrace();
            disconnectPlayer(event.getPlayer(), "Invalid handshake response.");
        }
    }

    private void disconnectPlayer(Player player, String reason) {
        try {
            PacketContainer kickPacket = protocolManager.createPacket(PacketType.Login.Server.DISCONNECT);
            kickPacket.getChatComponents().write(0, WrappedChatComponent.fromText(reason));
            protocolManager.sendServerPacket(player, kickPacket);

            plugin.getServer().getScheduler().runTask(plugin, () -> player.kickPlayer(reason));

            plugin.getLogger().info("Kicked " + player.getName() + ": " + reason);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to kick player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class HandshakePacket {
        public List<String> loaders;
        public String amVersion;
        public String mcVersion;

        public HandshakePacket(List<String> loaders, String amVersion, String mcVersion) {
            this.loaders = loaders;
            this.amVersion = amVersion;
            this.mcVersion = mcVersion;
        }

        public static HandshakePacket fromJson(String json) {
            Gson gson = new Gson();
            return gson.fromJson(json, HandshakePacket.class);
        }
    }

    public class DataPacket {
        public String address;
        public int port;
        public String modpackName;
        public Secrets.Secret secret;
        public boolean modRequired;
        public boolean requiresMagic;

        public DataPacket(String address, int port, String modpackName, Secrets.Secret secret, boolean modRequired, boolean requiresMagic) {
            this.address = address;
            this.port = port;
            this.modpackName = modpackName;
            this.secret = secret;
            this.modRequired = modRequired;
            this.requiresMagic = requiresMagic;
        }

        public String toJson() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }

        public static DataPacket fromJson(String json) {
            Gson gson = new Gson();
            return gson.fromJson(json, DataPacket.class);
        }
    }

}
