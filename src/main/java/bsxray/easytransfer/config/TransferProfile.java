package bsxray.easytransfer.config;

import java.util.Objects;

/**
 * Immutable model of a single transfer profile as defined
 * in the configuration file.
 * <p>
 * Each profile describes one server that can be moved from
 * one Pterodactyl node to another using a pre-configured
 * allocation port on the target node.
 *
 * @param serverId      Pterodactyl server ID (from the panel)
 * @param from          Name of the source node alias (must match a
 *                      key in the {@code [nodes]} section)
 * @param to            Name of the target node alias
 * @param allocationPort Port that is available on the target node
 */
public record TransferProfile(
        String serverId,
        String from,
        String to,
        int allocationPort
) {

    /**
     * Compact constructor performing validation.
     */
    public TransferProfile {
        Objects.requireNonNull(serverId, "serverId must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must not be blank");
        }
        if (from.isBlank()) {
            throw new IllegalArgumentException("from must not be blank");
        }
        if (to.isBlank()) {
            throw new IllegalArgumentException("to must not be blank");
        }
        if (allocationPort < 1 || allocationPort > 65535) {
            throw new IllegalArgumentException(
                    "allocationPort must be between 1 and 65535, got " + allocationPort);
        }
    }
}
