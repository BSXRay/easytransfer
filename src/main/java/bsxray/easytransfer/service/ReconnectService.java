package bsxray.easytransfer.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks which players were moved off a server during a transfer
 * and reconnects them once the server is available again.
 */
public class ReconnectService {

    private static final Logger log = LoggerFactory.getLogger(ReconnectService.class);

    private final ProxyServer proxy;
    private final ConcurrentMap<String, List<String>> pending =
            new ConcurrentHashMap<>();

    public ReconnectService(final ProxyServer proxy) {
        this.proxy = proxy;
    }

    /**
     * Moves all currently connected players of {@code fromServer}
     * to {@code toServer} and stores their usernames so they can
     * be reconnected later.
     *
     * @param fromServer Velocity server name (source)
     * @param toServer   Velocity server name (waiting server)
     */
    public void evictPlayers(final String fromServer, final String toServer) {
        proxy.getServer(fromServer).ifPresent(rs -> {
            final List<Player> players = new ArrayList<>(rs.getPlayersConnected());
            final List<String> names = players.stream()
                    .map(Player::getUsername)
                    .toList();
            pending.put(fromServer, names);

            if (toServer == null || toServer.isBlank()) {
                log.info("No waiting server configured; {} will be disconnected", names.size());
                return;
            }

            proxy.getServer(toServer).ifPresentOrElse(waiting -> {
                for (Player p : players) {
                    p.createConnectionRequest(waiting).fireAndForget();
                }
                log.info("Moved {} players from '{}' to '{}'", players.size(), fromServer, toServer);
            }, () -> log.warn("Waiting server '{}' is not registered on the proxy", toServer));
        });
    }

    /**
     * Connects all previously evicted players back to the given server.
     *
     * @param serverName Velocity server name
     */
    public void restorePlayers(final String serverName) {
        final List<String> names = pending.remove(serverName);
        if (names == null || names.isEmpty()) {
            return;
        }

        proxy.getServer(serverName).ifPresent(rs -> {
            final java.util.concurrent.atomic.AtomicInteger count =
                    new java.util.concurrent.atomic.AtomicInteger(0);
            for (String name : names) {
                proxy.getPlayer(name).ifPresent(player -> {
                    player.createConnectionRequest(rs).fireAndForget();
                    count.incrementAndGet();
                });
            }
            log.info("Restored {} players to '{}'", count.get(), serverName);
        });
    }
}
