package bsxray.easytransfer.config;

import static bsxray.easytransfer.util.Constants.CONFIG_ALIAS;
import static bsxray.easytransfer.util.Constants.CONFIG_PANEL_DOMAIN;
import static bsxray.easytransfer.util.Constants.CONFIG_PTLC_API_KEY;
import static bsxray.easytransfer.util.Constants.CONFIG_WAITING_SERVER;
import static bsxray.easytransfer.util.Constants.DEFAULT_ALIAS;
import static bsxray.easytransfer.util.Constants.PROFILE_ALLOC_PORT;
import static bsxray.easytransfer.util.Constants.PROFILE_FROM;
import static bsxray.easytransfer.util.Constants.PROFILE_SERVER_ID;
import static bsxray.easytransfer.util.Constants.PROFILE_TO;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles loading, parsing and reloading of
 * {@code easytransfer.conf}.
 * <p>
 * The config uses a simple line-based format:
 * <ul>
 *   <li>{@code key = value} for settings and node aliases</li>
 *   <li>A bare word starts a transfer profile block, followed
 *       by indented {@code key = value} lines</li>
 *   <li>{@code #} comments and empty lines are ignored</li>
 * </ul>
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private final File configFile;

    private String panelDomain   = "";
    private String apiKey   = "";
    private String waitingServer = "";
    private String alias         = DEFAULT_ALIAS;

    private final Map<String, String> nodes    = new LinkedHashMap<>();
    private final Map<String, TransferProfile> transfers = new LinkedHashMap<>();

    /**
     * Creates the manager and immediately loads (or creates) the config.
     *
     * @param dataDirectory plugin data directory
     */
    public ConfigManager(final File dataDirectory) {
        this.configFile = new File(dataDirectory, "easytransfer.conf");
        saveDefaultConfig();
        load();
    }

    /* ───── public API ───── */

    /** Returns the panel domain, may be empty. */
    public String panelDomain() {
        return panelDomain;
    }

    /** Returns the API key, may be empty. */
    public String apiKey() {
        return apiKey;
    }

    /** Returns the waiting server alias, may be empty (disabled). */
    public String waitingServer() {
        return waitingServer;
    }

    /** Returns the main command alias. Never null. */
    public String alias() {
        return alias;
    }

    /** Unmodifiable view of node aliases. */
    public Map<String, String> nodes() {
        return Map.copyOf(nodes);
    }

    /** Unmodifiable view of transfer profiles, keyed by name. */
    public Map<String, TransferProfile> transfers() {
        return Map.copyOf(transfers);
    }

    /** Returns the set of configured transfer names. */
    public Set<String> transferNames() {
        return transfers.keySet();
    }

    /**
     * Resolves a node alias to its numeric Pterodactyl node id.
     *
     * @param nodeName alias from the config
     * @return the node id string, or {@code null} if unknown
     */
    public String nodeId(final String nodeName) {
        return nodes.get(nodeName);
    }

    /**
     * Reloads the configuration from disk.
     * All previously parsed values are discarded.
     */
    public void reload() {
        nodes.clear();
        transfers.clear();
        load();
    }

    /* ───── internal helpers ───── */

    private void saveDefaultConfig() {
        if (configFile.exists()) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        configFile.getParentFile().mkdirs();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("easytransfer.conf")) {
            if (in != null) {
                Files.copy(in, configFile.toPath());
            } else {
                log.warn("Default config not found in classpath resources");
            }
        } catch (IOException e) {
            log.error("Failed to save default config", e);
        }
    }

    private void load() {
        if (!configFile.exists()) {
            log.warn("Config file not found at {}", configFile.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            parse(reader);
        } catch (IOException e) {
            log.error("Failed to load config", e);
        }

        log.info("Config loaded: {} nodes, {} transfers", nodes.size(), transfers.size());
    }

    /**
     * Line-by-line parser. See class javadoc for the expected format.
     */
    void parse(final BufferedReader reader) throws IOException {
        String line;
        boolean inNodes    = false;
        boolean inTransfer = false;
        String currentTransferName = null;
        String currentServerId     = null;
        String currentFrom         = null;
        String currentTo           = null;
        int    currentPort         = 0;
        int    lineNumber          = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            final String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            final int eqIdx = trimmed.indexOf('=');

            if (eqIdx >= 0) {
                // ── key = value line ──
                final String key   = trimmed.substring(0, eqIdx).trim();
                String       value = trimmed.substring(eqIdx + 1).trim();

                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                if (inTransfer) {
                    // part of a transfer profile
                    switch (key) {
                        case PROFILE_SERVER_ID -> currentServerId = value;
                        case PROFILE_FROM      -> currentFrom = value;
                        case PROFILE_TO        -> currentTo = value;
                        case PROFILE_ALLOC_PORT -> {
                            try {
                                currentPort = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid allocation_port at line {}: {}", lineNumber, value);
                            }
                        }
                        default -> log.debug("Ignoring unknown key '{}' at line {}", key, lineNumber);
                    }
                } else if (inNodes || !isSettingKey(key)) {
                    // node alias
                    inNodes = true;
                    nodes.put(key, value);
                } else {
                    // top-level setting
                    switch (key) {
                        case CONFIG_PANEL_DOMAIN   -> panelDomain = value;
                        case CONFIG_PTLC_API_KEY   -> apiKey = value;
                        case CONFIG_WAITING_SERVER -> waitingServer = value;
                        case CONFIG_ALIAS          -> alias = value;
                        default -> log.debug("Ignoring unknown setting '{}' at line {}", key, lineNumber);
                    }
                }

            } else {
                // ── line without '=' → transfer profile name ──
                flushTransfer(currentTransferName, currentServerId,
                        currentFrom, currentTo, currentPort);

                inTransfer          = true;
                inNodes             = false;
                currentTransferName = trimmed;
                currentServerId     = null;
                currentFrom         = null;
                currentTo           = null;
                currentPort         = 0;
            }
        }

        // last profile
        flushTransfer(currentTransferName, currentServerId,
                currentFrom, currentTo, currentPort);
    }

    private void flushTransfer(final String name,
                               final String serverId,
                               final String from,
                               final String to,
                               final int port) {
        if (name == null || serverId == null || from == null || to == null) {
            return;
        }
        try {
            transfers.put(name, new TransferProfile(serverId, from, to, port));
        } catch (IllegalArgumentException e) {
            log.warn("Skipping transfer profile '{}': {}", name, e.getMessage());
        }
    }

    private static boolean isSettingKey(final String key) {
        return CONFIG_PANEL_DOMAIN.equals(key)
                || CONFIG_PTLC_API_KEY.equals(key)
                || CONFIG_WAITING_SERVER.equals(key)
                || CONFIG_ALIAS.equals(key);
    }
}
