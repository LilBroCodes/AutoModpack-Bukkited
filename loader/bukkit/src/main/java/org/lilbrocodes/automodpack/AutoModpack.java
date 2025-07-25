package org.lilbrocodes.automodpack;

import org.bukkit.plugin.java.JavaPlugin;
import org.lilbrocodes.automodpack.command.AutoModpackCommand;

public final class AutoModpack extends JavaPlugin {
    public static final String PLUGIN_NAME = "§aAutomodpack";
    public static final String PLUGIN_ID = "automodpack";
    public static String PLUGIN_VERSION;

    @Override
    public void onEnable() {
        PLUGIN_VERSION = getDescription().getVersion();

        new AutoModpackCommand().register(this, "automodpack");
    }

    @Override
    public void onDisable() {

    }
}
