package org.lilbrocodes.automodpack;

import org.bukkit.plugin.java.JavaPlugin;
import org.lilbrocodes.automodpack.command.AutoModpackCommand;

public final class AutoModpack extends JavaPlugin {
    public static final String PLUGIN_NAME = "§aAutomodpack";
    public static final String PLUGIN_ID = "automodpack";
    public static String PLUGIN_VERSION;
    
    private AutoModpackServer server;

    @Override
    public void onEnable() {
        PLUGIN_VERSION = getDescription().getVersion();
        
        server = new AutoModpackServer(this);
        
        server.startServer();
        
        new AutoModpackCommand(server).register(this, "automodpack");
        
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
