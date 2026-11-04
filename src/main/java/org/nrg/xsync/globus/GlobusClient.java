package org.nrg.xsync.globus;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
     * @param completionTime the task's completion time (ISO-8601, as reported by Globus; may be null)
     */
    public record CompletedTask(String taskId, String completionTime) {
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
                    tasks.add(new CompletedTask(taskId, task.path("completion_time").asText(null)));
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
