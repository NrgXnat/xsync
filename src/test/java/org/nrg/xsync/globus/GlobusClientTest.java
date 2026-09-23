package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

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
}
