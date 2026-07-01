package bsxray.easytransfer.pterodactyl.model;

/**
 * Represents an IP/port allocation on a Pterodactyl node.
 *
 * @param id       allocation id (used in transfer requests)
 * @param ip       IP address of the allocation
 * @param port     port number
 * @param assigned whether the allocation is currently in use
 */
public record Allocation(
        int id,
        String ip,
        int port,
        boolean assigned
) {
}
