package org.nrg.xsync.globus;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.services.SerializerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Thin client over the Globus Transfer REST API: a collection-reachability
 * probe (used by the endpoint connection test) and the transfer-submission
 * operations (submission id, submit, task status, wait).
 *
 * <p>Base URL, operation paths, and document shapes are from the Globus
 * <a href="https://docs.globus.org/api/transfer/overview/">Transfer API
 * overview</a>,
 * <a href="https://docs.globus.org/api/transfer/file_operations/">file
 * operations</a>, and
 * <a href="https://docs.globus.org/api/transfer/task_submit/">task
 * submission</a>/<a href="https://docs.globus.org/api/transfer/task/">task</a>
 * references.</p>
 *
 * @author XSync
 */
@Slf4j
@Component
public class GlobusClient {

    /** Base URL of the Globus Transfer REST API. */
    public static final String TRANSFER_API_BASE = "https://transfer.api.globus.org/v0.10";

    private final ObjectMapper _objectMapper;

    @Autowired
    public GlobusClient(final SerializerService serializerService) {
        this(serializerService.getObjectMapper());
    }

    /** Constructor for tests (supply an {@link ObjectMapper} directly). */
    GlobusClient(final ObjectMapper objectMapper) {
        _objectMapper = objectMapper;
    }

    /**
     * Outcome of a collection-reachability probe.
     *
     * <p>Note the deliberate asymmetry: {@link #FORBIDDEN} is <em>not</em> a
     * hard failure. A caller with a per-peer subpath ACL (the normal inbox
     * arrangement) legitimately gets 403 when listing the collection root,
     * because it may only access its own subpath. Only {@link #NOT_FOUND}
     * (a bad/nonexistent collection UUID) and {@link #ERROR} indicate a
     * misconfiguration the connection test should surface.</p>
     */
    public enum Reachability {
        /** The collection exists and its root is listable by this token. */
        REACHABLE,
        /** The collection UUID does not resolve (Globus {@code ClientError.NotFound}). */
        NOT_FOUND,
        /** The collection exists but the token cannot list its root (403); expected for a subpath-scoped ACL. */
        FORBIDDEN,
        /** Any other failure (network, server error, unexpected status). */
        ERROR
    }

    /**
     * Parameters for a single-file Globus transfer (source collection/path to
     * destination collection/path).
     *
     * @param sourceCollectionId      the source guest-collection UUID
     * @param sourcePath              the file path within the source collection
     * @param destinationCollectionId the destination guest-collection UUID
     * @param destinationPath         the file path within the destination collection
     * @param label                   a human-readable label shown in Globus (may be blank)
     * @param verifyChecksum          whether Globus should verify the transfer by checksum
     */
    public record TransferRequest(String sourceCollectionId, String sourcePath,
                                  String destinationCollectionId, String destinationPath,
                                  String label, boolean verifyChecksum) {
    }

    /**
     * A Globus task's status, as returned by {@code GET /task/<id>}.
     *
     * @param status    one of {@code ACTIVE}, {@code INACTIVE}, {@code SUCCEEDED}, {@code FAILED}
     * @param niceStatus a finer-grained status for active/inactive tasks (may be null)
     * @param fatalError the fatal-error description for a failed task (may be null)
     */
    public record TaskStatus(String status, String niceStatus, String fatalError) {
        /** @return whether the task completed successfully. */
        public boolean isSuccessful() {
            return "SUCCEEDED".equals(status);
        }

        /** @return whether the task is still progressing (and worth polling again). */
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }

        /**
         * @return whether the task has reached a state that will not advance on
         *         its own — {@code SUCCEEDED}, {@code FAILED}, or {@code INACTIVE}
         *         (suspended awaiting intervention). Polling stops here.
         */
        public boolean isTerminal() {
            return !isActive();
        }
    }

    /**
     * A completed transfer task landing in a monitored collection, as returned
     * by the endpoint-manager {@code task_list} (the activity-monitor view).
     *
     * @param taskId         the Globus task id
     * @param ownerId        the identity id ({@code owner_id}) that submitted the task (may be null)
     * @param completionTime the task's completion time (ISO-8601, as reported by Globus; may be null)
     */
    public record CompletedTask(String taskId, String ownerId, String completionTime) {
    }

    /**
     * The kind of principal a Globus access rule grants to — the values
     * documented for the Transfer
     * <a href="https://docs.globus.org/api/transfer/permissions/">permissions</a>
     * API. An unrecognized value is rejected by {@link #fromWire} rather than
     * modeled, so this type cannot represent an invalid state. The API is
     * external and versioned, so a future principal type is possible; it surfaces
     * as a parse failure ({@code listAccessRules} wraps it and the reconciling
     * caller degrades to stored records), not as a silently-accepted sentinel.
     */
    public enum PrincipalType {
        IDENTITY("identity"),
        GROUP("group"),
        ALL_AUTHENTICATED_USERS("all_authenticated_users"),
        ANONYMOUS("anonymous");

        private final String wire;

        PrincipalType(final String wire) {
            this.wire = wire;
        }

        /** @return the Globus wire value. */
        public String wire() {
            return wire;
        }

        /**
         * @param wire the Globus {@code principal_type} string
         * @return the matching constant
         * @throws IllegalArgumentException if {@code wire} matches no known type
         *         (consistent with {@link Permission#fromWire}; the external API is versioned)
         */
        public static PrincipalType fromWire(final String wire) {
            for (final PrincipalType type : values()) {
                if (type.wire.equals(wire)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown Globus principal_type value: " + wire);
        }
    }

    /** The permission level a Globus access rule grants: read-only or read-write. */
    public enum Permission {
        READ("r"),
        READ_WRITE("rw");

        private final String wire;

        Permission(final String wire) {
            this.wire = wire;
        }

        /** @return the Globus wire value ({@code r} or {@code rw}). */
        public String wire() {
            return wire;
        }

        /**
         * @param wire the Globus {@code permissions} string
         * @return the matching constant
         * @throws IllegalArgumentException if {@code wire} is not {@code r} or {@code rw}
         *         (this is a closed two-value set; an unrecognized value is a real anomaly)
         */
        public static Permission fromWire(final String wire) {
            for (final Permission permission : values()) {
                if (permission.wire.equals(wire)) {
                    return permission;
                }
            }
            throw new IllegalArgumentException("Unknown Globus permission value: " + wire);
        }
    }

    /**
     * A guest-collection access-control rule, as returned by
     * {@code GET /endpoint/<id>/access_list} (Globus Transfer
     * <a href="https://docs.globus.org/api/transfer/permissions/">permissions</a>).
     *
     * <p>Invariant (enforced in the constructor): {@code principalType} and
     * {@code permissions} are non-null. {@code principal} is kept as the raw wire
     * value because its form varies by principal type — a Globus identity UUID
     * for {@link PrincipalType#IDENTITY} and {@link PrincipalType#GROUP}, but
     * empty/absent for {@link PrincipalType#ALL_AUTHENTICATED_USERS} and
     * {@link PrincipalType#ANONYMOUS} — so it cannot be an unconditional
     * {@link UUID}. Use {@link #identityId()} for the type-safe UUID view.</p>
     *
     * @param id            the access-rule id ({@code null} for implicit role-based rules)
     * @param principalType the kind of principal (never {@code null})
     * @param principal     the raw principal id (a Globus identity UUID for identity rules)
     * @param path          the collection-relative path the rule grants on
     * @param permissions   the permission level (never {@code null})
     */
    public record AccessRule(String id, PrincipalType principalType, String principal, String path,
                             Permission permissions) {

        /** Enforces the non-null invariants that make a rule well-formed. */
        public AccessRule {
            Objects.requireNonNull(principalType, "principalType");
            Objects.requireNonNull(permissions, "permissions");
        }

        /**
         * The principal as a Globus identity UUID, present only for an
         * {@link PrincipalType#IDENTITY} rule whose principal parses as a UUID.
         *
         * @return the identity UUID, or empty for non-identity rules or an unparseable principal
         */
        public Optional<UUID> identityId() {
            if (principalType != PrincipalType.IDENTITY || principal == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(principal));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Probe whether a collection is reachable with the given Transfer token by
     * listing its root. Classifies the result rather than throwing, so a caller
     * testing several collections can report each independently.
     *
     * @param accessToken a Transfer API bearer token (see {@link GlobusAuthService})
     * @param collectionId the collection (endpoint) UUID to probe
     * @return the {@link Reachability} classification
     */
    public Reachability probeCollection(final String accessToken, final String collectionId) {
        final String url = TRANSFER_API_BASE + "/operation/endpoint/" + collectionId + "/ls";
        try {
            listRoot(url, accessToken);
            return Reachability.REACHABLE;
        } catch (HttpStatusCodeException e) {
            final HttpStatus status = HttpStatus.resolve(e.getRawStatusCode());
            if (status == HttpStatus.NOT_FOUND) {
                log.info("Globus collection {} not found (bad UUID): {}", collectionId, e.getResponseBodyAsString());
                return Reachability.NOT_FOUND;
            }
            if (status == HttpStatus.FORBIDDEN) {
                log.debug("Globus collection {} root not listable (403); expected for a subpath-scoped ACL", collectionId);
                return Reachability.FORBIDDEN;
            }
            log.info("Globus ls on collection {} failed (HTTP {}): {}", collectionId,
                    e.getStatusCode(), e.getResponseBodyAsString());
            return Reachability.ERROR;
        } catch (RestClientException e) {
            log.info("Globus ls on collection {} failed: {}", collectionId, e.getMessage());
            return Reachability.ERROR;
        }
    }

    /**
     * Fetch a one-time submission id, required to submit a transfer. Fetching a
     * fresh id per submission makes a retried submit idempotent.
     *
     * @param accessToken a Transfer API bearer token
     * @return the submission id
     * @throws GlobusTransferException if the request fails or the response has no id
     */
    public String getSubmissionId(final String accessToken) {
        final String url = TRANSFER_API_BASE + "/submission_id";
        final String body = call("get submission id", () -> getForBody(url, accessToken));
        return requireField(body, "value", "submission id");
    }

    /**
     * Submit a transfer task. Fetches a submission id, builds the transfer
     * document, and posts it.
     *
     * @param accessToken a Transfer API bearer token
     * @param request     the transfer parameters
     * @return the Globus {@code task_id}
     * @throws GlobusTransferException if the request fails or the response has no task id
     */
    public String submitTransfer(final String accessToken, final TransferRequest request) {
        final String submissionId = getSubmissionId(accessToken);
        final String body;
        try {
            body = _objectMapper.writeValueAsString(buildTransferDocument(submissionId, request));
        } catch (JsonProcessingException e) {
            throw new GlobusTransferException("Could not build Globus transfer document", e);
        }
        final String url = TRANSFER_API_BASE + "/transfer";
        final String response = call("transfer submission", () -> postForBody(url, accessToken, body));
        return requireField(response, "task_id", "task id");
    }

    /**
     * Get the current status of a transfer task.
     *
     * @param accessToken a Transfer API bearer token
     * @param taskId      the task id from {@link #submitTransfer}
     * @return the task's {@link TaskStatus}
     * @throws GlobusTransferException if the request fails or the response has no status
     */
    public TaskStatus getTaskStatus(final String accessToken, final String taskId) {
        final String url = TRANSFER_API_BASE + "/task/" + taskId;
        final String body = call("task status", () -> getForBody(url, accessToken));
        try {
            final JsonNode node = _objectMapper.readTree(body);
            final String status = node.path("status").asText(null);
            if (StringUtils.isBlank(status)) {
                throw new GlobusTransferException("Globus task response missing status: " + body);
            }
            return new TaskStatus(status, node.path("nice_status").asText(null),
                    node.path("fatal_error").path("description").asText(null));
        } catch (JsonProcessingException e) {
            throw new GlobusTransferException("Could not parse Globus task response", e);
        }
    }

    /**
     * Block-poll a task until it reaches a terminal state or the timeout
     * elapses. This first cut mirrors the Aspera path's blocking wait; async
     * task tracking is a later option.
     *
     * @param accessToken    a Transfer API bearer token
     * @param taskId         the task id to wait on
     * @param timeoutMillis  give up after this long
     * @param pollIntervalMillis wait this long between status checks
     * @return the terminal {@link TaskStatus} (check {@link TaskStatus#isSuccessful()})
     * @throws GlobusTransferException if the timeout elapses or the wait is interrupted
     */
    public TaskStatus waitForTask(final String accessToken, final String taskId,
                                  final long timeoutMillis, final long pollIntervalMillis) {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            final TaskStatus taskStatus = getTaskStatus(accessToken, taskId);
            if (taskStatus.isTerminal()) {
                return taskStatus;
            }
            final long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new GlobusTransferException("Timed out after " + timeoutMillis
                        + " ms waiting for Globus task " + taskId + " (last status " + taskStatus.status() + ")");
            }
            try {
                Thread.sleep(Math.min(pollIntervalMillis, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GlobusTransferException("Interrupted while waiting for Globus task " + taskId, e);
            }
        }
    }

    /**
     * List successfully-completed transfers whose destination is the given
     * collection, using the endpoint-manager (activity-monitor) task list.
     *
     * <p>This is the upload service's completion signal: a transfer a user
     * pushed into the inbox is detected here only once it has reached
     * {@code SUCCEEDED}. The caller (the node service account) must hold the
     * {@code activity_monitor} effective role on the collection, or the list
     * comes back empty (Globus filters by privilege rather than erroring).</p>
     *
     * @param accessToken  a Transfer API bearer token for the node service account
     * @param collectionId the inbox collection UUID to monitor (as destination)
     * @param since        lower bound on completion time; {@code null} omits the
     *                     time filter and returns all SUCCEEDED tasks
     * @return the completed tasks, newest first as Globus returns them
     * @throws GlobusTransferException if the request fails or the response is unparseable
     */
    public List<CompletedTask> listCompletedTransfersTo(final String accessToken,
                                                                  final String collectionId, final Instant since) {
        // Values are URL-safe as-is (a collection UUID; an ISO-8601 datetime).
        // Do NOT percent-encode: filter_completion_time's separator comma and the
        // time's colons must reach Globus as literal characters — encoded forms
        // (%2C/%3A) are rejected with "Invalid date in filter_completion_time".
        // getForBody hands RestTemplate a URI so it won't re-encode them either.
        final StringBuilder url = new StringBuilder(TRANSFER_API_BASE)
                .append("/endpoint_manager/task_list")
                .append("?filter_endpoint=").append(collectionId)
                .append("&filter_endpoint_use=destination")
                .append("&filter_status=SUCCEEDED");
        if (since != null) {
            // filter_completion_time wants ISO-8601 at SECONDS precision; Instant's
            // default rendering carries nanoseconds, which Globus also rejects.
            // Truncate, then use an open-ended "start," window (blank end = no upper bound).
            final String start = since.truncatedTo(ChronoUnit.SECONDS).toString();
            url.append("&filter_completion_time=").append(start).append(',');
        }
        final String body = call("endpoint-manager task list", () -> getForBody(url.toString(), accessToken));
        try {
            final JsonNode data = _objectMapper.readTree(body).path("DATA");
            final List<CompletedTask> tasks = new ArrayList<>();
            for (final JsonNode task : data) {
                final String taskId = task.path("task_id").asText(null);
                if (StringUtils.isNotBlank(taskId)) {
                    tasks.add(new CompletedTask(taskId, task.path("owner_id").asText(null),
                            task.path("completion_time").asText(null)));
                }
            }
            return tasks;
        } catch (JsonProcessingException e) {
            throw new GlobusTransferException("Could not parse Globus endpoint-manager task list", e);
        }
    }

    /**
     * Get the destination paths a completed task wrote, using the
     * endpoint-manager {@code successful_transfers} sub-resource. Each path is a
     * file the task delivered into the destination collection.
     *
     * <p>Returns the first page only; a very large task may paginate via a
     * {@code next_marker} (not handled here — a spike single-archive upload is a
     * single transfer item, so one page suffices).</p>
     *
     * @param accessToken a Transfer API bearer token for the node service account
     * @param taskId      the completed task id (from {@link #listCompletedTransfersTo})
     * @return the collection-relative destination paths the task wrote
     * @throws GlobusTransferException if the request fails or the response is unparseable
     */
    public List<String> successfulDestinationPaths(final String accessToken, final String taskId) {
        final String url = TRANSFER_API_BASE + "/endpoint_manager/task/" + taskId + "/successful_transfers";
        final String body = call("endpoint-manager successful transfers", () -> getForBody(url, accessToken));
        try {
            final JsonNode data = _objectMapper.readTree(body).path("DATA");
            final List<String> paths = new ArrayList<>();
            for (final JsonNode item : data) {
                final String path = item.path("destination_path").asText(null);
                if (StringUtils.isNotBlank(path)) {
                    paths.add(path);
                }
            }
            return paths;
        } catch (JsonProcessingException e) {
            throw new GlobusTransferException("Could not parse Globus successful-transfers response", e);
        }
    }

    /**
     * List the access-control rules on a guest collection
     * ({@code GET /endpoint/<id>/access_list}). The caller's identity must hold
     * an {@code access_manager}, {@code administrator}, or
     * {@code restricted_administrator} effective role on the collection, or
     * Globus returns a permission error.
     *
     * @param accessToken  a Transfer API bearer token
     * @param collectionId the guest-collection UUID
     * @return the collection's access rules (identity rules carry a UUID {@code principal})
     * @throws GlobusTransferException if the request fails or the response is unparseable
     */
    public List<AccessRule> listAccessRules(final String accessToken, final String collectionId) {
        final String url = TRANSFER_API_BASE + "/endpoint/" + collectionId + "/access_list";
        final String body = call("list access rules", () -> getForBody(url, accessToken));
        try {
            final JsonNode data = _objectMapper.readTree(body).path("DATA");
            final List<AccessRule> rules = new ArrayList<>();
            for (final JsonNode rule : data) {
                rules.add(new AccessRule(
                        rule.path("id").asText(null),
                        PrincipalType.fromWire(rule.path("principal_type").asText(null)),
                        rule.path("principal").asText(null),
                        rule.path("path").asText(null),
                        Permission.fromWire(rule.path("permissions").asText(null))));
            }
            return rules;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // IllegalArgumentException covers an unrecognized permissions value (Permission.fromWire);
            // callers that reconcile (GlobusUploadPermissionManager.list) degrade to stored records.
            throw new GlobusTransferException("Could not parse Globus access_list response", e);
        }
    }

    /**
     * Create an identity access rule on a guest collection
     * ({@code POST /endpoint/<id>/access}). The caller's identity must hold an
     * {@code access_manager} or {@code administrator} role on the collection.
     *
     * @param accessToken      a Transfer API bearer token
     * @param collectionId     the guest-collection UUID
     * @param identityId       the Globus identity UUID to grant to
     * @param path             the collection-relative path to grant on (e.g. {@code /PROJECT/user/})
     * @param permissions      the permission level to grant
     * @return the created access-rule id ({@code access_id})
     * @throws GlobusTransferException if the request fails or the response has no id
     */
    public String createAccessRule(final String accessToken, final String collectionId, final UUID identityId,
                                   final String path, final Permission permissions) {
        final ObjectNode doc = _objectMapper.createObjectNode();
        doc.put("DATA_TYPE", "access");
        doc.put("principal_type", PrincipalType.IDENTITY.wire());
        doc.put("principal", identityId.toString());
        doc.put("path", path);
        doc.put("permissions", permissions.wire());
        final String body;
        try {
            body = _objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new GlobusTransferException("Could not build Globus access-rule document", e);
        }
        final String url = TRANSFER_API_BASE + "/endpoint/" + collectionId + "/access";
        final String response = call("create access rule", () -> postForBody(url, accessToken, body));
        return requireField(response, "access_id", "access rule id");
    }

    /**
     * Delete an access rule from a guest collection
     * ({@code DELETE /endpoint/<id>/access/<rule_id>}). The caller's identity
     * must hold an {@code access_manager}, {@code administrator}, or
     * {@code restricted_administrator} role on the collection.
     *
     * @param accessToken  a Transfer API bearer token
     * @param collectionId the guest-collection UUID
     * @param ruleId       the access-rule id to delete
     * @throws GlobusTransferException if the request fails
     */
    public void deleteAccessRule(final String accessToken, final String collectionId, final String ruleId) {
        final String url = TRANSFER_API_BASE + "/endpoint/" + collectionId + "/access/" + ruleId;
        call("delete access rule", () -> deleteForBody(url, accessToken));
    }

    /**
     * Build the {@code transfer} submission document for a single file.
     *
     * @param submissionId the one-time submission id
     * @param request      the transfer parameters
     * @return the transfer document
     */
    private ObjectNode buildTransferDocument(final String submissionId, final TransferRequest request) {
        final ObjectNode doc = _objectMapper.createObjectNode();
        doc.put("DATA_TYPE", "transfer");
        doc.put("submission_id", submissionId);
        doc.put("source_endpoint", request.sourceCollectionId());
        doc.put("destination_endpoint", request.destinationCollectionId());
        doc.put("verify_checksum", request.verifyChecksum());
        // Request data-channel encryption. The authoritative control is the
        // collections' force_encryption setting; this is client-side belt-and-
        // suspenders so a transfer is encrypted even if a collection lacks it.
        doc.put("encrypt_data", true);
        if (StringUtils.isNotBlank(request.label())) {
            doc.put("label", request.label());
        }
        final ArrayNode data = doc.putArray("DATA");
        final ObjectNode item = data.addObject();
        item.put("DATA_TYPE", "transfer_item");
        item.put("source_path", request.sourcePath());
        item.put("destination_path", request.destinationPath());
        item.put("recursive", false); // XSync transfers a single XAR file
        return doc;
    }

    /**
     * Run an HTTP call, wrapping any failure in a {@link GlobusTransferException}
     * that surfaces the Globus error body (without it, failures are opaque).
     *
     * @param operation short description for the error message
     * @param call      the HTTP call
     * @return the response body
     */
    private String call(final String operation, final Supplier<String> call) {
        try {
            return call.get();
        } catch (HttpStatusCodeException e) {
            throw new GlobusTransferException("Globus " + operation + " failed (HTTP " + e.getStatusCode()
                    + "): " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new GlobusTransferException("Globus " + operation + " failed", e);
        }
    }

    /**
     * Read a required top-level string field from a JSON response body.
     *
     * @param json  the response body
     * @param field the field name
     * @param what  a human description for the error message
     * @return the field value
     * @throws GlobusTransferException if the body is unparseable or the field is blank
     */
    private String requireField(final String json, final String field, final String what) {
        try {
            final String value = _objectMapper.readTree(json).path(field).asText(null);
            if (StringUtils.isBlank(value)) {
                throw new GlobusTransferException("Globus response missing " + what + ": " + json);
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new GlobusTransferException("Could not parse Globus response for " + what, e);
        }
    }

    /**
     * Issue a GET and return the raw body. HTTP seam: overridable in tests to
     * avoid network access.
     *
     * @param url the fully-formed URL
     * @param accessToken the bearer token
     * @return the response body
     */
    protected String getForBody(final String url, final String accessToken) {
        // Pass a URI, not a String: RestTemplate treats a String as a URI template
        // and would re-encode it, double-escaping query values we already built
        // with the exact literal characters Globus requires.
        return new RestTemplate()
                .exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), String.class)
                .getBody();
    }

    /**
     * Issue a JSON POST and return the raw body. HTTP seam: overridable in tests.
     *
     * @param url the fully-formed URL
     * @param accessToken the bearer token
     * @param jsonBody the request body
     * @return the response body
     */
    protected String postForBody(final String url, final String accessToken, final String jsonBody) {
        final HttpHeaders headers = bearer(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new RestTemplate()
                .exchange(URI.create(url), HttpMethod.POST, new HttpEntity<>(jsonBody, headers), String.class)
                .getBody();
    }

    /**
     * Issue a DELETE and return the raw body. HTTP seam: overridable in tests.
     *
     * @param url the fully-formed URL
     * @param accessToken the bearer token
     * @return the response body
     */
    protected String deleteForBody(final String url, final String accessToken) {
        return new RestTemplate()
                .exchange(URI.create(url), HttpMethod.DELETE, new HttpEntity<>(bearer(accessToken)), String.class)
                .getBody();
    }

    /**
     * The {@code ls} GET used by {@link #probeCollection}; a named seam so probe
     * tests can override it independently of the transfer calls.
     *
     * @param url the fully-formed {@code ls} URL
     * @param accessToken the bearer token
     * @return the response body
     */
    protected String listRoot(final String url, final String accessToken) {
        return getForBody(url, accessToken);
    }

    private static HttpHeaders bearer(final String accessToken) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }
}
