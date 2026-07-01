package bsxray.easytransfer.pterodactyl;

/**
 * Exception thrown when communication with the Pterodactyl
 * panel API fails (network error, non-2xx response, etc.).
 */
public class PterodactylException extends RuntimeException {

    public PterodactylException(final String message) {
        super(message);
    }

    public PterodactylException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
