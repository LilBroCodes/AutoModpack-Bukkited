package org.lilbrocodes.automodpack.network;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.lilbrocodes.automodpack.AutoModpack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HandshakeManager {
    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final Gson gson = new Gson();

    private static final String HANDSHAKE_CHANNEL = "automodpack:handshake";
    private static final String DATA_CHANNEL = "automodpack:data";
    
    public HandshakeManager(Plugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        registerHandshakePackets();
    }

    private void registerHandshakePackets() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                PacketType.Login.Client.START) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                plugin.getLogger().info("Client login detected, preparing handshake");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (event.getPlayer() != null && event.getPlayer().isOnline()) {
                        sendHandshakePacket(event.getPlayer());
                    }
                }, 5L);
            }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Login.Client.CUSTOM_PAYLOAD) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                String channel = packet.getStrings().read(0);
                
                if (HANDSHAKE_CHANNEL.equals(channel)) {
                    plugin.getLogger().info("Received handshake response from client");
                    handleHandshakeResponse(event);
                } else if (DATA_CHANNEL.equals(channel)) {
                    plugin.getLogger().info("Received data response from client");
                    handleDataResponse(event);
                }
            }
        });
    }

    private void sendHandshakePacket(Player player) {
        plugin.getLogger().info("Preparing to send handshake packet to " + player.getName());
        try {
            PacketContainer customPayload = protocolManager.createPacket(PacketType.Login.Server.CUSTOM_PAYLOAD);
            plugin.getLogger().info("Created PacketContainer: " + customPayload);

            Object handle = customPayload.getHandle();
            plugin.getLogger().info("Packet handle: " + (handle != null ? handle.getClass().getName() : "null"));

            Class<?> minecraftKeyClass = Class.forName("net.minecraft.resources.MinecraftKey");
            Object minecraftKey = minecraftKeyClass
                    .getConstructor(String.class)
                    .newInstance("automodpack:handshake");
            plugin.getLogger().info("Created MinecraftKey: " + minecraftKey);

            HandshakePacket handshakePacket = new HandshakePacket(
                    getAcceptedLoaders(),
                    AutoModpack.PLUGIN_VERSION,
                    getMinecraftVersion()
            );
            String jsonPayload = gson.toJson(handshakePacket);
            plugin.getLogger().info("Handshake payload JSON: " + jsonPayload);

            byte[] payloadBytes = jsonPayload.getBytes();
            ByteBuf byteBuf = Unpooled.wrappedBuffer(payloadBytes);

            Class<?> serializerClass = Class.forName("net.minecraft.network.PacketDataSerializer");
            Object packetDataSerializer = serializerClass
                    .getConstructor(ByteBuf.class)
                    .newInstance(byteBuf);
            plugin.getLogger().info("Created PacketDataSerializer: " + packetDataSerializer);

            Field channelField = handle.getClass().getDeclaredField("c");
            channelField.setAccessible(true);
            channelField.set(handle, minecraftKey);
            plugin.getLogger().info("Set MinecraftKey channel field");

            Field dataField = handle.getClass().getDeclaredField("d");
            dataField.setAccessible(true);
            dataField.set(handle, packetDataSerializer);
            plugin.getLogger().info("Set PacketDataSerializer payload field");

            protocolManager.sendServerPacket(player, customPayload);
            plugin.getLogger().info("Sent handshake packet to " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send handshake packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleHandshakeResponse(PacketEvent event) {
        try {
            PacketContainer packet = event.getPacket();
            byte[] data = packet.getByteArrays().read(0);
            String jsonResponse = new String(data);
            
            HandshakePacket clientHandshakePacket = gson.fromJson(jsonResponse, HandshakePacket.class);
            Player player = event.getPlayer();
            
            plugin.getLogger().info("Client handshake from " + player.getName() + ": " + 
                    "Loader: " + clientHandshakePacket.loaders.get(0) + ", " +
                    "Version: " + clientHandshakePacket.amVersion);
            
            if (!isCompatibleVersion(clientHandshakePacket.amVersion)) {
                String reason = "AutoModpack version mismatch! Please install " +
                        AutoModpack.PLUGIN_VERSION + " to play on this server.";
                disconnectPlayer(player, reason);
                return;
            }
            
            sendDataPacket(player);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling handshake response: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleDataResponse(PacketEvent event) {
        plugin.getLogger().info("Processed data response from " + event.getPlayer().getName());
    }
    
    private void sendDataPacket(Player player) {
        // TODO
    }
    
    private void disconnectPlayer(Player player, String reason) {
        PacketContainer disconnect = protocolManager.createPacket(PacketType.Login.Server.DISCONNECT);
        disconnect.getChatComponents().write(0, WrappedChatComponent.fromText(reason));
        protocolManager.sendServerPacket(player, disconnect);

        plugin.getServer().getScheduler().runTask(plugin, () -> player.kickPlayer(reason));
    }
    
    private boolean isCompatibleVersion(String clientVersion) {
        return clientVersion.equals(AutoModpack.PLUGIN_VERSION);
    }
    
    private List<String> getAcceptedLoaders() {
        List<String> loaders = new ArrayList<>();
        loaders.add("fabric");
        loaders.add("forge");
        loaders.add("neoforge");
        return loaders;
    }
    
    private String getMinecraftVersion() {
        return plugin.getServer().getBukkitVersion().split("-")[0];
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
    }
}