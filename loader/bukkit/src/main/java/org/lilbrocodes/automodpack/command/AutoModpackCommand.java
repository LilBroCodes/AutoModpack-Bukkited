package org.lilbrocodes.automodpack.command;

import org.lilbrocodes.automodpack.AutoModpack;
import org.lilbrocodes.commander.api.CommanderCommand;
import org.lilbrocodes.commander.api.executor.CommandGroupNode;
import org.lilbrocodes.commander.api.executor.CommandHybridNode;
import org.lilbrocodes.commander.api.executor.ExecutorNode;
import org.lilbrocodes.commander.api.util.StaticChatUtil;

public class AutoModpackCommand extends CommanderCommand {
    public AutoModpackCommand() {
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
    }

    @Override
    public void initialize(ExecutorNode<CommandGroupNode> rootNode) {
        if (!(rootNode instanceof CommandGroupNode root)) return;


    }
}
