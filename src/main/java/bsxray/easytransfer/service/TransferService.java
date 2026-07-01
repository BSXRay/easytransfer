package bsxray.easytransfer.service;

import static bsxray.easytransfer.util.Constants.PING_TIMEOUT_SECONDS;
import static bsxray.easytransfer.util.Constants.START_POLL_INTERVAL_MS;
import static bsxray.easytransfer.util.Constants.START_TIMEOUT_MS;
import static bsxray.easytransfer.util.Constants.STOP_POLL_INTERVAL_MS;
import static bsxray.easytransfer.util.Constants.STOP_TIMEOUT_MS;
import static bsxray.easytransfer.util.Constants.TRANSFER_POLL_INTERVAL_MS;
import static bsxray.easytransfer.util.Constants.TRANSFER_TIMEOUT_MS;

import bsxray.easytransfer.config.ConfigManager;
import bsxray.easytransfer.config.TransferProfile;
import bsxray.easytransfer.pterodactyl.PterodactylClient;
import bsxray.easytransfer.pterodactyl.model.Allocation;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates a full Pterodactyl server transfer:
 * <ol>
 *   <li>move players to the waiting server</li>
 *   <li>stop the server</li>
 *   <li>transfer to the target node</li>
 *   <li>update the Velocity server registration</li>
 *   <li>start the server</li>
 *   <li>wait for panel confirmation</li>
 *   <li>restore players</li>
 * </ol>
 */
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final Object             plugin;
    private final ProxyServer        proxy;
    private final ConfigManager      config;
    private final PterodactylClient  pterodactyl;
    private final ReconnectService   reconnect;

    public TransferService(final Object plugin,
                           final ProxyServer proxy,
                           final ConfigManager config,
                           final PterodactylClient pterodactyl,
                           final ReconnectService reconnect) {
        this.plugin      = plugin;
        this.proxy       = proxy;
        this.config      = config;
        this.pterodactyl = pterodactyl;
        this.reconnect   = reconnect;
    }

    /**
     * Initiates an asynchronous transfer for the named profile.
     *
     * @param transferName key from the config's transfer section
     * @param source       command sender (for progress messages)
     */
    public void startTransfer(final String transferName, final CommandSource source) {
        final TransferProfile profile = config.transfers().get(transferName);
        if (profile == null) {
            source.sendMessage(Component.text("Unknown transfer profile: " + transferName,
                    NamedTextColor.RED));
            return;
        }

        final String node1Str = config.nodeId(profile.from());
        final String node2Str = config.nodeId(profile.to());

        if (node1Str == null || node2Str == null) {
            source.sendMessage(Component.text(
                    "Node alias not found – check that both '%s' and '%s' are defined"
                            .formatted(profile.from(), profile.to()),
                    NamedTextColor.RED));
            return;
        }

        int node1, node2;
        try {
            node1 = Integer.parseInt(node1Str);
            node2 = Integer.parseInt(node2Str);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text(
                    "Node ids must be numeric, got '%s' / '%s'".formatted(node1Str, node2Str),
                    NamedTextColor.RED));
            return;
        }

        source.sendMessage(Component.text("Starting transfer: " + transferName,
                NamedTextColor.GREEN));

        proxy.getScheduler().buildTask(plugin, () -> {
            try {
                execute(transferName, profile, node1, node2, source);
            } catch (Exception e) {
                log.error("Transfer '{}' failed", transferName, e);
                source.sendMessage(Component.text("Transfer failed: " + e.getMessage(),
                        NamedTextColor.RED));
            }
        }).schedule();
    }

    /* ───── internal orchestration ───── */

    private void execute(final String profileName,
                         final TransferProfile profile,
                         final int fromNode,
                         final int toNode,
                         final CommandSource source) throws Exception {

        final String serverId   = profile.serverId();
        final int    targetPort = profile.allocationPort();
        final String serverName = pterodactyl.fetchServerName(serverId);
        final String waiting    = config.waitingServer();

        /* 1 ── Evacuate players ── */
        if (!waiting.isBlank()) {
            info(source, "Moving players to '" + waiting + "' …");
            reconnect.evictPlayers(serverName, waiting);
        }

        /* 2 ── Stop ── */
        info(source, "Stopping server …");
        pterodactyl.powerAction(serverId, "stop");
        info(source, "Waiting for server to go offline …");
        awaitOffline(serverName);

        /* 3 ── Find target allocation ── */
        info(source, "Looking up port " + targetPort + " on node " + toNode + " …");
        final Allocation targetAlloc = pterodactyl.findFreeAllocation(toNode, targetPort);

        /* 4 ── Transfer ── */
        info(source, "Transferring to node " + toNode + " …");
        pterodactyl.transferServer(serverId, toNode, targetAlloc.id);

        /* 5 ── Wait for transfer to finish ── */
        info(source, "Waiting for transfer to complete …");
        awaitCondition("transfer completion",
                () -> !pterodactyl.isTransferring(serverId),
                TRANSFER_POLL_INTERVAL_MS, TRANSFER_TIMEOUT_MS);

        /* 6 ── Re-register in Velocity with the new address ── */
        info(source, "Updating server address …");
        final Allocation newAlloc = pterodactyl.findAssignedAllocation(toNode, targetPort);
        proxy.unregisterServer(serverName);
        proxy.registerServer(ServerInfo.create(serverName,
                InetSocketAddress.createUnresolved(newAlloc.ip(), newAlloc.port())));
        log.info("Server '{}' re-registered at {}:{}", serverName, newAlloc.ip(), newAlloc.port());

        /* 7 ── Start ── */
        info(source, "Starting server …");
        pterodactyl.powerAction(serverId, "start");

        /* 8 ── Wait for online (Velocity ping + panel state) ── */
        info(source, "Waiting for server to come online …");
        awaitOnline(serverName);
        info(source, "Verifying panel state …");
        awaitPanelRunning(serverId);

        /* 9 ── Restore players ── */
        if (!waiting.isBlank()) {
            info(source, "Restoring players …");
            reconnect.restorePlayers(serverName);
        }

        source.sendMessage(Component.text("✔ Transfer '" + profileName + "' completed!",
                NamedTextColor.GREEN));
    }

    /* ───── await helpers ───── */

    private void awaitCondition(final String label,
                                final Check condition,
                                final int intervalMs,
                                final int timeoutMs) throws Exception {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.isMet()) return;
            Thread.sleep(intervalMs);
        }
        throw new RuntimeException("Timed out waiting for " + label);
    }

    private void awaitOnline(final String serverName) throws Exception {
        final long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            final var opt = proxy.getServer(serverName);
            if (opt.isPresent()) {
                try {
                    opt.get().ping().get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return;
                } catch (Exception ignored) {
                    // not reachable yet
                }
            }
            Thread.sleep(START_POLL_INTERVAL_MS);
        }
        throw new RuntimeException("Server '" + serverName + "' did not come online");
    }

    private void awaitOffline(final String serverName) throws Exception {
        final long deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            final var opt = proxy.getServer(serverName);
            if (opt.isEmpty()) return;
            try {
                opt.get().ping().get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                return; // ping failed → offline
            }
            Thread.sleep(STOP_POLL_INTERVAL_MS);
        }
        log.warn("Server '{}' did not go offline within timeout, proceeding anyway", serverName);
    }

    private void awaitPanelRunning(final String serverId) throws Exception {
        final long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (pterodactyl.isPanelRunning(serverId)) {
                log.info("Panel confirms server {} is running", serverId);
                return;
            }
            Thread.sleep(START_POLL_INTERVAL_MS);
        }
        log.warn("Panel did not confirm running state for server {}, proceeding", serverId);
    }

    @FunctionalInterface
    private interface Check {
        boolean isMet() throws Exception;
    }

    private static void info(final CommandSource source, final String msg) {
        source.sendMessage(Component.text(msg, NamedTextColor.GRAY));
    }

}
