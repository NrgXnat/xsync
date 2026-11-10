package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Unit tests for {@link GlobusClient#probeCollection}, exercising the
 * HTTP-status classification via the overridable {@link GlobusClient#listRoot}
 * seam (no network).
 */
class GlobusClientTest {

    /** Test double: records the URL and returns a body, or throws a supplied error. */
    private static final class StubClient extends GlobusClient {
        private final RuntimeException toThrow;
        private String lastUrl;

        StubClient(final RuntimeException toThrow) {
            super(new ObjectMapper());
            this.toThrow = toThrow;
        }

        @Override
        protected String listRoot(final String url, final String accessToken) {
            lastUrl = url;
            if (toThrow != null) {
                throw toThrow;
            }
            return "{\"DATA\":[]}";
        }
    }

    private static HttpClientErrorException clientError(final HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(),
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }

    @Test
    void successfulListingIsReachable() {
        final StubClient client = new StubClient(null);
        assertEquals(GlobusClient.Reachability.REACHABLE, client.probeCollection("token", "collection-uuid"));
    }

    @Test
    void probeBuildsTheLsUrlForTheCollection() {
        final StubClient client = new StubClient(null);
        client.probeCollection("token", "collection-uuid");
        assertTrue(client.lastUrl.startsWith(GlobusClient.TRANSFER_API_BASE),
                "URL should target the Transfer API base");
        assertTrue(client.lastUrl.contains("/operation/endpoint/collection-uuid/ls"),
                "URL should be the ls operation for the collection: " + client.lastUrl);
    }

    @Test
    void notFoundStatusMapsToNotFound() {
        final StubClient client = new StubClient(clientError(HttpStatus.NOT_FOUND));
        assertEquals(GlobusClient.Reachability.NOT_FOUND, client.probeCollection("token", "bad-uuid"));
    }

    @Test
    void forbiddenStatusIsTolerated() {
        final StubClient client = new StubClient(clientError(HttpStatus.FORBIDDEN));
        assertEquals(GlobusClient.Reachability.FORBIDDEN, client.probeCollection("token", "subpath-scoped"),
                "403 is expected for a per-peer subpath ACL and must not be a hard failure");
    }

    @Test
    void otherClientErrorMapsToError() {
        final StubClient client = new StubClient(clientError(HttpStatus.BAD_REQUEST));
        assertEquals(GlobusClient.Reachability.ERROR, client.probeCollection("token", "collection-uuid"));
    }

    @Test
    void serverErrorMapsToError() {
        final StubClient client = new StubClient(
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "boom",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        assertEquals(GlobusClient.Reachability.ERROR, client.probeCollection("token", "collection-uuid"));
    }

    @Test
    void transportFailureMapsToError() {
        final StubClient client = new StubClient(new ResourceAccessException("connection refused"));
        assertEquals(GlobusClient.Reachability.ERROR, client.probeCollection("token", "collection-uuid"));
    }

    // --- transfer operations ----------------------------------------------

    /** Test double for the GET/POST seams used by the transfer methods. */
    private static final class TransferStub extends GlobusClient {
        private Function<String, String> onGet = url -> "{}";
        private RuntimeException getError;
        private String postResult = "{}";
        private RuntimeException postError;
        private String lastPostUrl;
        private String lastPostBody;

        TransferStub() {
            super(new ObjectMapper());
        }

        @Override
        protected String getForBody(final String url, final String accessToken) {
            if (getError != null) {
                throw getError;
            }
            return onGet.apply(url);
        }

        @Override
        protected String postForBody(final String url, final String accessToken, final String jsonBody) {
            lastPostUrl = url;
            lastPostBody = jsonBody;
            if (postError != null) {
                throw postError;
            }
            return postResult;
        }
    }

    @Test
    void getSubmissionIdReturnsValue() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"DATA_TYPE\":\"submission_id\",\"value\":\"sub-123\"}";
        assertEquals("sub-123", client.getSubmissionId("token"));
    }

    @Test
    void getSubmissionIdMissingValueThrows() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"DATA_TYPE\":\"submission_id\"}";
        assertThrows(GlobusTransferException.class, () -> client.getSubmissionId("token"));
    }

    @Test
    void httpErrorSurfacesTheGlobusErrorBody() {
        final TransferStub client = new TransferStub();
        client.getError = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, "{\"code\":\"BadRequest\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        final GlobusTransferException ex =
                assertThrows(GlobusTransferException.class, () -> client.getSubmissionId("token"));
        assertTrue(ex.getMessage().contains("BadRequest"),
                "the Globus error body should be surfaced in the exception: " + ex.getMessage());
    }

    @Test
    void submitTransferBuildsDocumentAndReturnsTaskId() throws Exception {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"value\":\"sub-1\"}";
        client.postResult = "{\"DATA_TYPE\":\"transfer_result\",\"task_id\":\"task-9\",\"code\":\"Accepted\"}";

        final GlobusClient.TransferRequest request = new GlobusClient.TransferRequest(
                "src-coll", "/outbox/opaque.xar", "dst-coll", "/inbox/peer/opaque.xar", "XSync sync", true);
        final String taskId = client.submitTransfer("token", request);

        assertEquals("task-9", taskId);
        assertTrue(client.lastPostUrl.endsWith("/transfer"));

        final JsonNode doc = new ObjectMapper().readTree(client.lastPostBody);
        assertEquals("transfer", doc.get("DATA_TYPE").asText());
        assertEquals("sub-1", doc.get("submission_id").asText());
        assertEquals("src-coll", doc.get("source_endpoint").asText());
        assertEquals("dst-coll", doc.get("destination_endpoint").asText());
        assertTrue(doc.get("verify_checksum").asBoolean());
        assertTrue(doc.get("encrypt_data").asBoolean(), "transfers must request data-channel encryption");
        assertEquals("XSync sync", doc.get("label").asText());
        final JsonNode item = doc.get("DATA").get(0);
        assertEquals("transfer_item", item.get("DATA_TYPE").asText());
        assertEquals("/outbox/opaque.xar", item.get("source_path").asText());
        assertEquals("/inbox/peer/opaque.xar", item.get("destination_path").asText());
        assertFalse(item.get("recursive").asBoolean());
    }

    @Test
    void submitTransferOmitsBlankLabel() throws Exception {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"value\":\"sub-1\"}";
        client.postResult = "{\"task_id\":\"task-9\"}";

        client.submitTransfer("token",
                new GlobusClient.TransferRequest("src", "/a", "dst", "/b", "  ", true));

        final JsonNode doc = new ObjectMapper().readTree(client.lastPostBody);
        assertFalse(doc.has("label"), "a blank label must be omitted from the transfer document");
    }

    @Test
    void getTaskStatusParsesFailedTaskFields() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"DATA_TYPE\":\"task\",\"status\":\"FAILED\",\"nice_status\":\"PERMISSION_DENIED\","
                + "\"fatal_error\":{\"code\":\"PermissionDenied\",\"description\":\"no access\"}}";

        final GlobusClient.TaskStatus status = client.getTaskStatus("token", "task-9");
        assertEquals("FAILED", status.status());
        assertEquals("PERMISSION_DENIED", status.niceStatus());
        assertEquals("no access", status.fatalError());
        assertFalse(status.isSuccessful());
        assertFalse(status.isActive());
        assertTrue(status.isTerminal());
    }

    @Test
    void getTaskStatusParsesSucceededTask() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"DATA_TYPE\":\"task\",\"status\":\"SUCCEEDED\"}";

        final GlobusClient.TaskStatus status = client.getTaskStatus("token", "task-9");
        assertTrue(status.isSuccessful());
        assertTrue(status.isTerminal());
        assertNull(status.fatalError());
    }

    @Test
    void getTaskStatusMissingStatusThrows() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"DATA_TYPE\":\"task\"}";
        assertThrows(GlobusTransferException.class, () -> client.getTaskStatus("token", "task-9"));
    }

    @Test
    void waitForTaskPollsUntilTerminal() {
        final TransferStub client = new TransferStub();
        final int[] calls = {0};
        client.onGet = url -> (++calls[0] == 1) ? "{\"status\":\"ACTIVE\"}" : "{\"status\":\"SUCCEEDED\"}";

        final GlobusClient.TaskStatus status = client.waitForTask("token", "task-9", 5000, 1);
        assertTrue(status.isSuccessful());
        assertTrue(calls[0] >= 2, "should have polled more than once");
    }

    @Test
    void waitForTaskReturnsFailedTerminalStatus() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"status\":\"FAILED\",\"fatal_error\":{\"description\":\"boom\"}}";

        final GlobusClient.TaskStatus status = client.waitForTask("token", "task-9", 5000, 1);
        assertFalse(status.isSuccessful());
        assertEquals("FAILED", status.status());
    }

    @Test
    void waitForTaskTimesOut() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"status\":\"ACTIVE\"}";
        assertThrows(GlobusTransferException.class, () -> client.waitForTask("token", "task-9", 0, 5));
    }

    // --- endpoint-manager activity monitoring (upload-service detection) ---

    @Test
    void listCompletedTransfersBuildsFilteredUrlAndParsesTasks() {
        final TransferStub client = new TransferStub();
        final String[] seenUrl = {null};
        client.onGet = url -> {
            seenUrl[0] = url;
            return "{\"DATA_TYPE\":\"task_list\",\"DATA\":["
                    + "{\"task_id\":\"t-1\",\"owner_id\":\"id-1\",\"status\":\"SUCCEEDED\","
                    + "\"completion_time\":\"2026-10-28T00:00:00Z\"},"
                    + "{\"task_id\":\"t-2\",\"status\":\"SUCCEEDED\",\"completion_time\":\"2026-10-28T01:00:00Z\"}]}";
        };

        final java.util.List<GlobusClient.CompletedTask> tasks =
                client.listCompletedTransfersTo("token", "inbox-coll", java.time.Instant.parse("2026-10-27T00:00:00Z"));

        assertEquals(2, tasks.size());
        assertEquals("t-1", tasks.get(0).taskId());
        assertEquals("id-1", tasks.get(0).ownerId());
        assertEquals("2026-10-28T01:00:00Z", tasks.get(1).completionTime());
        assertTrue(seenUrl[0].contains("/endpoint_manager/task_list"), seenUrl[0]);
        assertTrue(seenUrl[0].contains("filter_endpoint=inbox-coll"), seenUrl[0]);
        assertTrue(seenUrl[0].contains("filter_endpoint_use=destination"), seenUrl[0]);
        assertTrue(seenUrl[0].contains("filter_status=SUCCEEDED"), seenUrl[0]);
        // Literal (unencoded) datetime + comma: Globus rejects percent-encoded
        // colons/comma in filter_completion_time.
        assertTrue(seenUrl[0].contains("filter_completion_time=2026-10-27T00:00:00Z,"),
                "completion-time filter must use a literal ISO datetime and open-ended comma: " + seenUrl[0]);
    }

    @Test
    void listCompletedTransfersTruncatesSubSecondPrecision() {
        // Globus rejects filter_completion_time with fractional seconds
        // ("Invalid date in filter_completion_time"); the client must truncate.
        final TransferStub client = new TransferStub();
        final String[] seenUrl = {null};
        client.onGet = url -> {
            seenUrl[0] = url;
            return "{\"DATA\":[]}";
        };

        client.listCompletedTransfersTo("token", "inbox-coll",
                java.time.Instant.parse("2026-10-28T20:51:44.709287542Z"));

        assertTrue(seenUrl[0].contains("filter_completion_time=2026-10-28T20:51:44Z,"),
                "sub-second precision must be truncated to seconds: " + seenUrl[0]);
        assertFalse(seenUrl[0].contains("709287542"), "nanoseconds must not appear in the filter: " + seenUrl[0]);
    }

    @Test
    void listCompletedTransfersOmitsTimeFilterWhenSinceNull() {
        final TransferStub client = new TransferStub();
        final String[] seenUrl = {null};
        client.onGet = url -> {
            seenUrl[0] = url;
            return "{\"DATA\":[]}";
        };

        final java.util.List<GlobusClient.CompletedTask> tasks =
                client.listCompletedTransfersTo("token", "inbox-coll", null);

        assertTrue(tasks.isEmpty());
        assertFalse(seenUrl[0].contains("filter_completion_time"),
                "no completion-time filter when 'since' is null: " + seenUrl[0]);
    }

    @Test
    void listCompletedTransfersSkipsEntriesWithoutTaskId() {
        final TransferStub client = new TransferStub();
        client.onGet = url -> "{\"DATA\":[{\"status\":\"SUCCEEDED\"},{\"task_id\":\"t-9\"}]}";
        final java.util.List<GlobusClient.CompletedTask> tasks =
                client.listCompletedTransfersTo("token", "inbox-coll", null);
        assertEquals(1, tasks.size());
        assertEquals("t-9", tasks.get(0).taskId());
    }

    @Test
    void successfulDestinationPathsParsesAndBuildsUrl() {
        final TransferStub client = new TransferStub();
        final String[] seenUrl = {null};
        client.onGet = url -> {
            seenUrl[0] = url;
            return "{\"DATA_TYPE\":\"successful_transfers\",\"DATA\":["
                    + "{\"DATA_TYPE\":\"successful_transfer\",\"source_path\":\"/a\",\"destination_path\":\"/PRJ/alice/study.tgz\"}]}";
        };

        final java.util.List<String> paths = client.successfulDestinationPaths("token", "t-1");

        assertEquals(1, paths.size());
        assertEquals("/PRJ/alice/study.tgz", paths.get(0));
        assertTrue(seenUrl[0].endsWith("/endpoint_manager/task/t-1/successful_transfers"), seenUrl[0]);
    }

    @Test
    void successfulDestinationPathsHandlesEmptyAndSurfacesErrors() {
        final TransferStub empty = new TransferStub();
        empty.onGet = url -> "{\"DATA\":[]}";
        assertTrue(empty.successfulDestinationPaths("token", "t-1").isEmpty());

        final TransferStub failing = new TransferStub();
        failing.getError = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, "{\"code\":\"BadRequest\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThrows(GlobusTransferException.class, () -> failing.successfulDestinationPaths("token", "t-1"));
    }
}
