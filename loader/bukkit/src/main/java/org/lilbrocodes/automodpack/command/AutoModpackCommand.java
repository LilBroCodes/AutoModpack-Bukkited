package org.lilbrocodes.automodpack.command;

import org.lilbrocodes.automodpack.AutoModpack;
import org.lilbrocodes.automodpack.AutoModpackServer;
import org.lilbrocodes.commander.api.CommanderCommand;
import org.lilbrocodes.commander.api.executor.CommandActionNode;
import org.lilbrocodes.commander.api.executor.CommandGroupNode;
import org.lilbrocodes.commander.api.executor.CommandHybridNode;
import org.lilbrocodes.commander.api.executor.ExecutorNode;
import org.lilbrocodes.commander.api.util.StaticChatUtil;

import java.util.List;

public class AutoModpackCommand extends CommanderCommand {
    private final AutoModpackServer server;

    public AutoModpackCommand(AutoModpackServer server) {
        super (
            new CommandHybridNode(
                    "automodpack",
                    "Root command of automodpack",
                    AutoModpack.PLUGIN_NAME,
                    (sender, args) -> {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, String.format("Running %s§r version §a%s", AutoModpack.PLUGIN_NAME, AutoModpack.PLUGIN_VERSION));
                    }
            ),
            true
        );
        this.server = server;
    }

    @Override
    public void initialize(ExecutorNode<CommandGroupNode> rootNode) {
        if (!(rootNode instanceof CommandGroupNode root)) return;

        CommandActionNode generateNode = new CommandActionNode(
                "generate",
                "Generate modpack",
                AutoModpack.PLUGIN_NAME,
                List.of(),
                (sender, args) -> {
                    if (!sender.hasPermission("automodpack.generate")) {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "You don't have permission to generate a modpack!");
                        return;
                    }
                    StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Generating modpack...");
                    boolean success = server.generateModpack();
                    if (success) {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Modpack generated successfully!");
                    } else {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "Failed to generate modpack!");
                    }
                }
        );
        root.addChild(generateNode);

        CommandHybridNode hostNode = new CommandHybridNode(
                "host",
                "Modpack host commands",
                AutoModpack.PLUGIN_NAME,
                (sender, args) -> {
                    if (!sender.hasPermission("automodpack.host")) {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "You don't have permission to manage the modpack host!");
                        return;
                    }
                    boolean isRunning = server.isRunning();
                    String status = isRunning ? "§arunning" : "§cnot running";
                    StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Modpack host status: " + status);
                }
        );
        root.addChild(hostNode);

        CommandActionNode startNode = new CommandActionNode(
                "start",
                "Start modpack host",
                AutoModpack.PLUGIN_NAME,
                List.of(),
                (sender, args) -> {
                    if (!sender.hasPermission("automodpack.host.start")) {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "You don't have permission to start the modpack host!");
                        return;
                    }
                    if (server.isRunning()) {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Modpack host is already running!");
                        return;
                    }
                    StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Starting modpack host...");
                    boolean success = server.startServer();
                    if (success) {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Modpack host started successfully!");
                    } else {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "Failed to start modpack host!");
                    }
                }
        );
        hostNode.addChild(startNode);

        CommandActionNode stopNode = new CommandActionNode(
                "stop",
                "Stop modpack host",
                AutoModpack.PLUGIN_NAME,
                List.of(),
                (sender, args) -> {
                    if (!sender.hasPermission("automodpack.host.stop")) {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "You don't have permission to stop the modpack host!");
                        return;
                    }
                    if (!server.isRunning()) {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Modpack host is not running!");
                        return;
                    }
                    StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Stopping modpack host...");
                    boolean success = server.stopServer();
                    if (success) {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Modpack host stopped successfully!");
                    } else {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "Failed to stop modpack host!");
                    }
                }
        );
        hostNode.addChild(stopNode);

        CommandActionNode restartNode = new CommandActionNode(
                "restart",
                "Restart modpack host",
                AutoModpack.PLUGIN_NAME,
                List.of(),
                (sender, args) -> {
                    if (!sender.hasPermission("automodpack.host.restart")) {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "You don't have permission to restart the modpack host!");
                        return;
                    }
                    StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Restarting modpack host...");
                    boolean success = server.restartServer();
                    if (success) {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Modpack host restarted successfully!");
                    } else {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "Failed to restart modpack host!");
                    }
                }
        );
        hostNode.addChild(restartNode);

        CommandActionNode fingerprintNode = new CommandActionNode(
                "fingerprint",
                "Get certificate fingerprint",
                AutoModpack.PLUGIN_NAME,
                List.of(),
                (sender, args) -> {
                    if (!sender.hasPermission("automodpack.host.fingerprint")) {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "You don't have permission to view the certificate fingerprint!");
                        return;
                    }
                    String fingerprint = server.getCertificateFingerprint();
                    if (fingerprint != null) {
                        StaticChatUtil.info(sender, AutoModpack.PLUGIN_NAME, "Certificate fingerprint: §e" + fingerprint);
                    } else {
                        StaticChatUtil.error(sender, AutoModpack.PLUGIN_NAME, "Certificate fingerprint is not available. Make sure the server is running with TLS enabled.");
                    }
                }
        );
        hostNode.addChild(fingerprintNode);
    }
}
