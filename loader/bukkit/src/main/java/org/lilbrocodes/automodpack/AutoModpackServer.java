package org.lilbrocodes.automodpack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import pl.skidam.automodpack_core.GlobalVariables;
import pl.skidam.automodpack_core.config.ConfigTools;
import pl.skidam.automodpack_core.config.Jsons;
import pl.skidam.automodpack_core.modpack.ModpackContent;
import pl.skidam.automodpack_core.modpack.ModpackExecutor;
import pl.skidam.automodpack_core.protocol.netty.NettyServer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static pl.skidam.automodpack_core.GlobalVariables.*;

public class AutoModpackServer {
    private final JavaPlugin plugin;
    private NettyServer server;
    private ModpackExecutor modpackExecutor;
    private ModpackContent modpackContent;
    private boolean isRunning = false;

    public AutoModpackServer(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeDirectories();
        loadConfiguration();
    }

    private void initializeDirectories() {
        File pluginDataFolder = plugin.getDataFolder();
        if (!pluginDataFolder.exists()) {
            pluginDataFolder.mkdirs();
        }

        // Set up the global variables for paths
        automodpackDir.toFile().mkdirs();
        hostModpackDir.toFile().mkdirs();
        hostContentModpackDir.toFile().mkdirs();
        privateDir.toFile().mkdirs();
        modpacksDir.toFile().mkdirs();

        serverConfigFile = automodpackDir.resolve("automodpack-server.json");
        serverCoreConfigFile = automodpackDir.resolve("automodpack-core.json");
        hostModpackContentFile = hostModpackDir.resolve("automodpack-content.json");
    }

    private void loadConfiguration() {
        serverConfig = ConfigTools.load(serverConfigFile, Jsons.ServerConfigFieldsV2.class);
        if (serverConfig == null) {
            serverConfig = new Jsons.ServerConfigFieldsV2();
            serverConfig.DO_NOT_CHANGE_IT = 2;
            serverConfig.modpackName = "BukkitModpack";
            serverConfig.modpackHost = true;
            serverConfig.generateModpackOnStart = true;
            serverConfig.bindPort = 25566;
            serverConfig.syncedFiles = new ArrayList<>();
            serverConfig.syncedFiles.add("mods/*.jar");
            ConfigTools.save(serverConfigFile, serverConfig);
        }

        Jsons.ServerCoreConfigFields serverCoreConfig = ConfigTools.load(serverCoreConfigFile, Jsons.ServerCoreConfigFields.class);
        if (serverCoreConfig == null) {
            serverCoreConfig = new Jsons.ServerCoreConfigFields();
            serverCoreConfig.automodpackVersion = plugin.getDescription().getVersion();
            serverCoreConfig.mcVersion = Bukkit.getBukkitVersion().split("-")[0];
            serverCoreConfig.loader = "bukkit";
            serverCoreConfig.loaderVersion = Bukkit.getBukkitVersion();
            ConfigTools.save(serverCoreConfigFile, serverCoreConfig);
        }

        AM_VERSION = serverCoreConfig.automodpackVersion;
        MC_VERSION = serverCoreConfig.mcVersion;
        LOADER = serverCoreConfig.loader;
        LOADER_VERSION = serverCoreConfig.loaderVersion;
    }

    public boolean startServer() {
        if (isRunning) {
            return true;
        }

        modpackExecutor = new ModpackExecutor();
        GlobalVariables.modpackExecutor = modpackExecutor;

        Path mainModpackDir = hostContentModpackDir;
        modpackContent = new ModpackContent(
                serverConfig.modpackName,
                null,
                mainModpackDir,
                serverConfig.syncedFiles,
                serverConfig.allowEditsInFiles,
                serverConfig.forceCopyFilesToStandardLocation,
                modpackExecutor.getExecutor()
        );

        if (serverConfig.generateModpackOnStart) {
            CompletableFuture.runAsync(() -> {
                boolean generated = modpackExecutor.generateNew(modpackContent);
                if (generated) {
                    LOGGER.info("Modpack generated successfully!");
                } else {
                    LOGGER.error("Failed to generate modpack!");
                }
            });
        }

        server = new NettyServer();
        hostServer = server;

        var channelFuture = server.start();
        isRunning = channelFuture.isPresent();

        if (isRunning) {
            LOGGER.info("AutoModpack server started on port {}", serverConfig.bindPort);
        } else if (server.shouldHost()) {
            isRunning = true;
            LOGGER.info("AutoModpack server hosted on Minecraft port");
        } else {
            LOGGER.error("Failed to start AutoModpack server");
        }

        return isRunning;
    }

    public boolean stopServer() {
        if (!isRunning) {
            return true;
        }

        boolean stopped = server.stop();
        if (stopped) {
            LOGGER.info("AutoModpack server stopped");
            isRunning = false;

            // Stop the modpack executor
            if (modpackExecutor != null) {
                modpackExecutor.stop();
                modpackExecutor = null;
            }
        } else {
            LOGGER.error("Failed to stop AutoModpack server");
        }

        return stopped;
    }

    public boolean restartServer() {
        boolean stopped = stopServer();
        if (stopped) {
            return startServer();
        }
        return false;
    }

    public boolean generateModpack() {
        if (modpackExecutor == null || modpackContent == null) {
            LOGGER.error("Cannot generate modpack: server not started");
            return false;
        }

        boolean generated = modpackExecutor.generateNew(modpackContent);
        if (generated) {
            LOGGER.info("Modpack generated successfully!");
        } else {
            LOGGER.error("Failed to generate modpack!");
        }

        return generated;
    }

    public boolean isRunning() {
        return isRunning && (server != null && server.isRunning());
    }

    public String getCertificateFingerprint() {
        if (server != null) {
            return server.getCertificateFingerprint();
        }
        return null;
    }
}