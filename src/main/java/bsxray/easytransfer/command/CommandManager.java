package bsxray.easytransfer.command;

import bsxray.easytransfer.config.ConfigManager;
import com.velocitypowered.api.command.CommandMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of the EasyTransfer command:
 * registration, un-registration and reload.
 */
public class CommandManager {

    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final Object                               plugin;
    private final com.velocitypowered.api.command.CommandManager nativeManager;
    private final EasyTransferCommand                  command;

    private volatile CommandMeta meta;

    public CommandManager(final Object plugin,
                          final com.velocitypowered.api.command.CommandManager nativeManager,
                          final EasyTransferCommand command) {
        this.plugin         = plugin;
        this.nativeManager  = nativeManager;
        this.command        = command;
    }

    /**
     * (Re-)registers the command using the alias from the config.
     * Called on plugin init and on every {@code /<alias> reload}.
     */
    public void register(final ConfigManager config) {
        if (meta != null) {
            nativeManager.unregister(meta);
        }
        final String alias = config.alias();
        meta = nativeManager.metaBuilder(alias)
                .aliases("et")
                .plugin(plugin)
                .build();
        nativeManager.register(meta, command);
        log.info("Registered command /{}", alias);
    }

    /** Un-registers the command (called on proxy shutdown). */
    public void unregister() {
        if (meta != null) {
            nativeManager.unregister(meta);
            meta = null;
        }
    }
}
