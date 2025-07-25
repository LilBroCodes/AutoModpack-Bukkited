package org.lilbrocodes.automodpack;

import org.bukkit.plugin.java.JavaPlugin;
import org.lilbrocodes.automodpack.command.AutoModpackCommand;
import org.lilbrocodes.automodpack.network.HandshakeManager;

public final class AutoModpack extends JavaPlugin {
    public static final String PLUGIN_NAME = "§aAutomodpack";
    public static final String PLUGIN_ID = "automodpack";
    public static String PLUGIN_VERSION;
    
    private AutoModpackServer server;
    private HandshakeManager handshakeManager;

    @Override
    public void onEnable() {
        PLUGIN_VERSION = getDescription().getVersion();
        
        server = new AutoModpackServer(this);
        server.startServer();
        
        new AutoModpackCommand(server).register(this, "automodpack");
        
        try {
            if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
                handshakeManager = new HandshakeManager(this);
                getLogger().info("Successfully initialized AutoModpack handshake protocol");
            } else {
                getLogger().severe("ProtocolLib not found! Client detection will not work.");
                getLogger().severe("Please install ProtocolLib to enable client detection.");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to initialize handshake protocol: " + e.getMessage());
            e.printStackTrace();
        }
        
        getLogger().info(PLUGIN_NAME + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stopServer();
        }
        
        getLogger().info(PLUGIN_NAME + " has been disabled!");
    }
}
