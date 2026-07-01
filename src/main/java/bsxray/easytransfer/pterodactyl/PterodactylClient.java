package bsxray.easytransfer.pterodactyl;

import static bsxray.easytransfer.util.Constants.API_CLIENT_POWER;
import static bsxray.easytransfer.util.Constants.API_CLIENT_RESOURCES;
import static bsxray.easytransfer.util.Constants.API_NODE_ALLOCATIONS;
import static bsxray.easytransfer.util.Constants.API_POWER;
import static bsxray.easytransfer.util.Constants.API_SERVERS;
import static bsxray.easytransfer.util.Constants.API_TRANSFER;
import static bsxray.easytransfer.util.Constants.HTTP_TIMEOUT_SECONDS;
import static bsxray.easytransfer.util.Constants.STATE_RUNNING;
import static bsxray.easytransfer.util.Constants.STATE_TRANSFERRING;

import bsxray.easytransfer.pterodactyl.model.Allocation;
import bsxray.easytransfer.pterodactyl.model.ServerResources;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for the Pterodactyl Panel API v1
 * (Application API + limited Client API support).
 * <p>
 * All methods throw {@link PterodactylException} on failure.
 */
public class PterodactylClient {

    private static final Logger log = LoggerFactory.getLogger(PterodactylClient.class);

    private final HttpClient http;

    private volatile String baseUrl;
    private volatile String apiKey;
    private volatile String clientKey;

    public PterodactylClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();
    }

    /* ───── credential management ───── */

    /** Updates stored panel URL and API keys (called on init & reload). */
    public void updateCredentials(final String panelDomain,
                                   final String apiKey,
                                   final String clientKey) {
        String domain = panelDomain;
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://" + domain;
        }
        this.baseUrl   = domain;
        this.apiKey    = apiKey;
        this.clientKey = clientKey;
        log.info("PterodactylClient credentials updated (base: {})", domain);
    }

    /* ───── high-level API methods ───── */

    /**
     * Sends a power action to the server (start / stop / restart / kill)
     * via the Client API.
     *
     * @param serverId Pterodactyl application server id (numeric)
     * @param signal   power signal
     */
    public void powerAction(final String serverId, final String signal) {
        log.info("Sending power action '{}' to server {}", signal, serverId);
        final String identifier = fetchServerIdentifier(serverId);
        clientPost(endpoint(API_CLIENT_POWER.formatted(identifier)),
                "{\"signal\":\"" + signal + "\"}");
    }

    /**
     * Initiates a server transfer to a different node.
     *
     * @param serverId     Pterodactyl application server id
     * @param targetNodeId numeric node id of the target node
     * @param allocationId allocation id on the target node
     */
    public void transferServer(final String serverId,
                               final int targetNodeId,
                               final int allocationId) {
        log.info("Transferring server {} to node {} allocation {}",
                serverId, targetNodeId, allocationId);
        post(endpoint(API_SERVERS + serverId + API_TRANSFER),
                "{\"node_id\":" + targetNodeId + ",\"allocation\":" + allocationId + "}");
    }

    /**
     * Returns the server's display name from the panel.
     *
     * @param serverId Pterodactyl application server id
     * @return server name
     */
    public String fetchServerName(final String serverId) {
        final JsonObject attrs = fetchAttributes(API_SERVERS + serverId);
        return attrs.get("name").getAsString();
    }

    /**
     * Returns the server's current install/transfer status
     * ({@code null} when idle, {@code "transferring"} during transfer…).
     */
    public String fetchServerStatus(final String serverId) {
        final JsonObject attrs = fetchAttributes(API_SERVERS + serverId);
        final JsonElement status = attrs.get("status");
        return status.isJsonNull() ? null : status.getAsString();
    }

    /**
     * Checks whether the server is currently in transferring state.
     */
    public boolean isTransferring(final String serverId) {
        return STATE_TRANSFERRING.equals(fetchServerStatus(serverId));
    }

    /**
     * Finds a free (unassigned) allocation on the given node for the
     * requested port, or throws if none exists.
     */
    public Allocation findFreeAllocation(final int nodeId, final int port) {
        return findAllocation(nodeId, port, true);
    }

    /**
     * Finds any allocation (assigned <em>or</em> free) on the given
     * node for the requested port.
     */
    public Allocation findAssignedAllocation(final int nodeId, final int port) {
        return findAllocation(nodeId, port, false);
    }

    /**
     * Queries the Client API to obtain the server's current power state.
     * <p>
     * This endpoint usually requires a {@code ptlc_} (Client) API key.
     * Returns {@link Optional#empty()} if the call fails.
     */
    public Optional<ServerResources> fetchServerResources(final String serverId) {
        try {
            final String identifier = fetchServerIdentifier(serverId);
            final String body = clientGet(endpoint(API_CLIENT_RESOURCES.formatted(identifier)));
            final JsonObject attrs = JsonParser.parseString(body)
                    .getAsJsonObject()
                    .getAsJsonObject("attributes");
            final String state = attrs.get("current_state").getAsString();
            return Optional.of(new ServerResources(state));
        } catch (Exception e) {
            log.warn("Could not fetch server resources (client API key may be required): {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Checks via the panel whether the server is marked as running.
     */
    public boolean isPanelRunning(final String serverId) {
        return fetchServerResources(serverId)
                .map(ServerResources::isRunning)
                .orElse(false);
    }

    /* ───── internal HTTP helpers ───── */

    private String endpoint(final String path) {
        return baseUrl + path;
    }

    private HttpRequest.Builder request(final String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS));
    }

    private HttpRequest.Builder clientRequest(final String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + clientKey)
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS));
    }

    private String get(final String url) {
        try {
            final HttpRequest req = request(url).GET().build();
            final HttpResponse<String> res = http.send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new PterodactylException(
                        "GET %s returned %d: %s".formatted(url, res.statusCode(), res.body()));
            }
            return res.body();
        } catch (PterodactylException e) {
            throw e;
        } catch (Exception e) {
            throw new PterodactylException("GET %s failed".formatted(url), e);
        }
    }

    private String clientGet(final String url) {
        try {
            final HttpRequest req = clientRequest(url).GET().build();
            final HttpResponse<String> res = http.send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new PterodactylException(
                        "GET %s returned %d: %s".formatted(url, res.statusCode(), res.body()));
            }
            return res.body();
        } catch (PterodactylException e) {
            throw e;
        } catch (Exception e) {
            throw new PterodactylException("GET %s failed".formatted(url), e);
        }
    }

    private void post(final String url, final String jsonBody) {
        try {
            final HttpRequest req = request(url)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(jsonBody))
                    .build();
            final HttpResponse<String> res = http.send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new PterodactylException(
                        "POST %s returned %d: %s".formatted(url, res.statusCode(), res.body()));
            }
        } catch (PterodactylException e) {
            throw e;
        } catch (Exception e) {
            throw new PterodactylException("POST %s failed".formatted(url), e);
        }
    }

    private void clientPost(final String url, final String jsonBody) {
        try {
            final HttpRequest req = clientRequest(url)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(jsonBody))
                    .build();
            final HttpResponse<String> res = http.send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new PterodactylException(
                        "POST %s returned %d: %s".formatted(url, res.statusCode(), res.body()));
            }
        } catch (PterodactylException e) {
            throw e;
        } catch (Exception e) {
            throw new PterodactylException("POST %s failed".formatted(url), e);
        }
    }

    private JsonObject fetchAttributes(final String path) {
        final String body = get(endpoint(path));
        return JsonParser.parseString(body)
                .getAsJsonObject()
                .getAsJsonObject("attributes");
    }

    private String fetchServerIdentifier(final String serverId) {
        final JsonObject attrs = fetchAttributes(API_SERVERS + serverId);
        return attrs.get("identifier").getAsString();
    }

    private Allocation findAllocation(final int nodeId,
                                      final int port,
                                      final boolean requireFree) {
        int page       = 1;
        int totalPages = 1;

        while (page <= totalPages) {
            final String url = endpoint(API_NODE_ALLOCATIONS.formatted(nodeId))
                    + "?page=" + page;
            final String body = get(url);
            final JsonObject obj = JsonParser.parseString(body).getAsJsonObject();

            final JsonObject meta = obj.getAsJsonObject("meta");
            if (meta != null) {
                final JsonObject pagination = meta.getAsJsonObject("pagination");
                if (pagination != null) {
                    totalPages = pagination.get("total_pages").getAsInt();
                }
            }

            final JsonArray data = obj.getAsJsonArray("data");
            for (JsonElement el : data) {
                final JsonObject attrs = el.getAsJsonObject()
                        .getAsJsonObject("attributes");
                final int     allocPort = attrs.get("port").getAsInt();
                final boolean assigned  = attrs.get("assigned").getAsBoolean();

                if (allocPort == port && (!requireFree || !assigned)) {
                    final int    id = attrs.get("id").getAsInt();
                    final String ip = attrs.get("ip").getAsString();
                    log.info("Found allocation {} ({}:{}) on node {}", id, ip, port, nodeId);
                    return new Allocation(id, ip, port, assigned);
                }
            }
            page++;
        }

        throw new PterodactylException(
                "No allocation for port %d on node %d".formatted(port, nodeId));
    }
}
