package bsxray.easytransfer.pterodactyl.model;

/**
 * Lightweight representation of the server resource / power
 * state returned by the Pterodactyl Client API.
 *
 * @param currentState one of {@code running}, {@code offline},
 *                     {@code starting}, {@code stopping}
 */
public record ServerResources(
        String currentState
) {

    /** Convenience check for the {@code running} state. */
    public boolean isRunning() {
        return "running".equals(currentState);
    }
}
