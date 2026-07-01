package bsxray.easytransfer;

import bsxray.easytransfer.command.CommandManager;
import bsxray.easytransfer.command.EasyTransferCommand;
import bsxray.easytransfer.config.ConfigManager;
import bsxray.easytransfer.pterodactyl.PterodactylClient;
import bsxray.easytransfer.service.ReconnectService;
import bsxray.easytransfer.service.TransferService;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EasyTransfer – Velocity plugin for automated Pterodactyl
 * server transfers between nodes.
 * <p>
 * Players are evacuated to a waiting server during the transfer
 * and automatically reconnected once the server is running again
 * on the target node.
 */
@Plugin(
        id = "easytransfer",
        name = "EasyTransfer",
        version = "1.0",
        url = "https://github.com/bsxray/easytransfer",
        description = "Transfer Pterodactyl game servers between nodes",
        authors = {"bsxray"}
)
public class EasyTransfer {

    static final Logger log = LoggerFactory.getLogger(EasyTransfer.class);

    private final ProxyServer    proxy;
    private final Path           dataDir;

    private ConfigManager       config;
    private PterodactylClient   pterodactyl;
    private ReconnectService    reconnect;
    private TransferService     transferService;
    private CommandManager      commandManager;

    @Inject
    public EasyTransfer(final ProxyServer proxy,
                        final Logger logger,
                        @DataDirectory final Path dataDir) {
        this.proxy   = proxy;
        this.dataDir = dataDir;
    }

    /* ───── lifecycle ───── */

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        /* config */
        this.config = new ConfigManager(dataDir.toFile());

        /* pterodactyl api client */
        this.pterodactyl = new PterodactylClient();
        this.pterodactyl.updateCredentials(config.panelDomain(), config.apiKey());

        /* services */
        this.reconnect       = new ReconnectService(proxy);
        this.transferService = new TransferService(this, proxy, config, pterodactyl, reconnect);

        /* command */
        final Runnable reloadAction = this::reload;
        final var cmd = new EasyTransferCommand(config, transferService, reloadAction);

        this.commandManager = new CommandManager(this, proxy.getCommandManager(), cmd);
        this.commandManager.register(config);

        log.info("EasyTransfer v1.0 initialized");
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        if (commandManager != null) {
            commandManager.unregister();
        }
    }

    /* ───── reload ───── */

    /** Reloads config, reconnects API and re-registers the command. */
    public void reload() {
        config.reload();
        pterodactyl.updateCredentials(config.panelDomain(), config.apiKey());
        commandManager.register(config);
        log.info("EasyTransfer reloaded");
    }
}
