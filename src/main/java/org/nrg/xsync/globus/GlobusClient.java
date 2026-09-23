package org.nrg.xsync.globus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Thin client over the Globus Transfer REST API. Currently exposes only a
 * lightweight collection-reachability probe used by the endpoint connection
 * test; the transfer-submission calls will be added with the send path.
 *
 * <p>Base URL and the {@code ls} operation path are from the Globus
 * <a href="https://docs.globus.org/api/transfer/overview/">Transfer API
 * overview</a> and
 * <a href="https://docs.globus.org/api/transfer/file_operations/">file
 * operations</a> reference.</p>
 *
 * @author XSync
 */
@Slf4j
@Component
public class GlobusClient {

    /** Base URL of the Globus Transfer REST API. */
    public static final String TRANSFER_API_BASE = "https://transfer.api.globus.org/v0.10";

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
     * Issue the {@code ls} GET and return the raw body. HTTP seam: overridable
     * in tests to avoid network access.
     *
     * @param url the fully-formed {@code ls} URL
     * @param accessToken the bearer token
     * @return the response body
     */
    protected String listRoot(final String url, final String accessToken) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new RestTemplate()
                .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getBody();
    }
}
