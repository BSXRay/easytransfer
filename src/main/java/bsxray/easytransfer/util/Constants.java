package bsxray.easytransfer.util;

/**
 * Central constant definitions for EasyTransfer.
 * <p>
 * All timeouts, config keys, permissions and similar
 * magic values live here instead of being scattered
 * across the codebase.
 */
public final class Constants {

    private Constants() {
    }

    /* ───── Config file keys ───── */

    public static final String CONFIG_PANEL_DOMAIN    = "panel_domain";
    public static final String CONFIG_PTLC_API_KEY    = "ptlc_api_key";
    public static final String CONFIG_WAITING_SERVER  = "waiting_server";
    public static final String CONFIG_ALIAS           = "alias";

    public static final String DEFAULT_ALIAS = "easytransfer";

    /* ───── Timeouts & intervals (milliseconds) ───── */

    public static final int STOP_POLL_INTERVAL_MS      = 3_000;
    public static final int STOP_TIMEOUT_MS            = 120_000;
    public static final int TRANSFER_POLL_INTERVAL_MS  = 10_000;
    public static final int TRANSFER_TIMEOUT_MS        = 600_000;
    public static final int START_POLL_INTERVAL_MS     = 5_000;
    public static final int START_TIMEOUT_MS           = 300_000;
    public static final int PING_TIMEOUT_SECONDS       = 5;
    public static final int HTTP_TIMEOUT_SECONDS       = 30;

    /* ───── Permission ───── */

    public static final String PERMISSION_ADMIN = "easytransfer.admin";

    /* ───── API paths ───── */

    public static final String API_SERVERS           = "/api/application/servers/";
    public static final String API_POWER             = "/power";
    public static final String API_TRANSFER          = "/transfer";
    public static final String API_NODE_ALLOCATIONS  = "/api/application/nodes/%d/allocations";
    public static final String API_CLIENT_RESOURCES  = "/api/client/servers/%s/resources";

    /* ───── Server state constants ───── */

    public static final String STATE_RUNNING      = "running";
    public static final String STATE_TRANSFERRING = "transferring";

    /* ───── Transfer profile keys ───── */

    public static final String PROFILE_SERVER_ID      = "server_id";
    public static final String PROFILE_FROM           = "from";
    public static final String PROFILE_TO             = "to";
    public static final String PROFILE_ALLOC_PORT     = "allocation_port";

    private static final java.util.Set<String> SETTING_KEYS = java.util.Set.of(
            CONFIG_PANEL_DOMAIN,
            CONFIG_PTLC_API_KEY,
            CONFIG_WAITING_SERVER,
            CONFIG_ALIAS
    );

    /**
     * Returns the set of known top-level config keys.
     */
    public static java.util.Set<String> settingKeys() {
        return SETTING_KEYS;
    }
}
