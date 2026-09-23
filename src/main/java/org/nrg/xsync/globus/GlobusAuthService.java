package org.nrg.xsync.globus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.services.SerializerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Obtains and caches Globus Transfer access tokens using the OAuth2
 * <em>client-credentials</em> grant.
 *
 * <p>XSync drives Globus transfers unattended, so it authenticates as a
 * confidential client (its own service identity) rather than on behalf of a
 * user. The client-credentials grant returns access tokens only (no refresh
 * token), so an expired token is simply re-requested.</p>
 *
 * <p>This service is <strong>storage-agnostic</strong>: callers supply
 * {@link GlobusCredentials}; how those are persisted (Hibernate entity,
 * encrypted preference, &hellip;) is decided elsewhere. Tokens are cached per
 * {@code (client id + scope)} until shortly before their stated expiry and
 * shared across syncs.</p>
 *
 * <p><strong>Scope.</strong> Only the base Transfer scope
 * ({@link #TRANSFER_SCOPE}) is requested. XSync transfers exclusively against
 * Globus <em>guest</em> collections, whose access is governed by ACLs plus the
 * base Transfer scope. The per-collection {@code data_access} dependent scope
 * is valid <strong>only</strong> for non-High-Assurance <em>mapped</em>
 * collections; requesting it for a guest collection is rejected with
 * {@code UNKNOWN_SCOPE_ERROR}. See the Globus
 * <a href="https://docs.globus.org/globus-connect-server/v5/application/">application
 * access guide</a> and the globus-sdk {@code add_dependent_data_access_scope}
 * documentation. Because the scope is constant, the cache holds effectively one
 * token per client, reused across every guest collection its ACLs cover.</p>
 *
 * @author XSync
 */
@Service
@Slf4j
public class GlobusAuthService {

    /** Default Globus Auth OAuth2 token endpoint. */
    public static final String DEFAULT_TOKEN_ENDPOINT = "https://auth.globus.org/v2/oauth2/token";

    /** Base Globus Transfer API scope. */
    public static final String TRANSFER_SCOPE = "urn:globus:auth:scope:transfer.api.globus.org:all";

    /** Resource server whose token is used for Transfer API calls. */
    public static final String TRANSFER_RESOURCE_SERVER = "transfer.api.globus.org";

    /** Re-request a token this long before its stated expiry. */
    private static final long EXPIRY_SAFETY_MARGIN_MS = TimeUnit.MINUTES.toMillis(5);

    private final String                          _tokenEndpoint;
    private final ObjectMapper                    _objectMapper;
    private final ConcurrentHashMap<String, CachedToken> _cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object>      _locks = new ConcurrentHashMap<>();

    @Autowired
    public GlobusAuthService(final SerializerService serializerService) {
        this(DEFAULT_TOKEN_ENDPOINT, serializerService.getObjectMapper());
    }

    /** Constructor for a custom token endpoint (and tests). */
    GlobusAuthService(final String tokenEndpoint, final ObjectMapper objectMapper) {
        _tokenEndpoint = tokenEndpoint;
        _objectMapper = objectMapper;
    }

    /**
     * Get a valid Globus Transfer access token for the given credentials,
     * scoped for {@code data_access} on the given collections. Returns a cached
     * token when one is still valid; otherwise requests a new one.
     *
     * @param credentials             the confidential-client credentials
     * @param dataAccessCollectionIds collection UUIDs the transfer will touch
     *                                (source and destination); may be empty
     * @return a bearer access token for the Transfer API
     * @throws GlobusAuthException if a token cannot be obtained
     */
    public String getTransferToken(final GlobusCredentials credentials, final Collection<String> dataAccessCollectionIds) {
        return getTransferToken(credentials, dataAccessCollectionIds, false);
    }

    /**
     * Get a Globus Transfer access token, optionally forcing a fresh request.
     *
     * <p>The cache is keyed on {@code (client id + scope)}, not the secret, so a
     * cached token can outlive a credential change. Callers that must observe
     * the <em>current</em> credentials&mdash;notably connection tests&mdash;pass
     * {@code forceRefresh = true} to bypass the cache read and mint a new token.
     * A successful forced fetch still updates the cache, replacing any stale
     * entry.</p>
     *
     * @param credentials             the confidential-client credentials
     * @param dataAccessCollectionIds collection UUIDs the transfer will touch
     * @param forceRefresh            if {@code true}, ignore any cached token and
     *                                request a new one
     * @return a bearer access token for the Transfer API
     * @throws GlobusAuthException if a token cannot be obtained
     */
    public String getTransferToken(final GlobusCredentials credentials, final Collection<String> dataAccessCollectionIds,
                                   final boolean forceRefresh) {
        // Guest collections are authorized by ACL + the base Transfer scope; no
        // per-collection data_access dependent scope (see class Javadoc). The
        // collection ids are retained for a forthcoming reachability check (an
        // ls in connection Test) and do not affect the requested scope.
        final String scope = TRANSFER_SCOPE;
        final String cacheKey = credentials.clientId() + "|" + scope;

        if (!forceRefresh) {
            final CachedToken cached = _cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.accessToken();
            }
        }
        // Serialize refreshes for the same key so concurrent syncs don't stampede.
        synchronized (_locks.computeIfAbsent(cacheKey, k -> new Object())) {
            if (!forceRefresh) {
                final CachedToken again = _cache.get(cacheKey);
                if (again != null && !again.isExpired()) {
                    return again.accessToken();
                }
            }
            final CachedToken fresh = parseTransferToken(postTokenRequest(credentials, scope));
            _cache.put(cacheKey, fresh);
            return fresh.accessToken();
        }
    }

    /**
     * Confirm that credentials can obtain a Transfer token (a lightweight
     * "test connection" at the auth layer, without moving any data).
     *
     * <p>Always requests a fresh token ({@code forceRefresh}), so the result
     * reflects the supplied credentials rather than a possibly-stale cached
     * token from before a credential change.</p>
     *
     * @param credentials             the credentials to test
     * @param dataAccessCollectionIds collections to include in the scope
     * @return {@code true} if a token was obtained
     */
    public boolean verifyCredentials(final GlobusCredentials credentials, final Collection<String> dataAccessCollectionIds) {
        try {
            return StringUtils.isNotBlank(getTransferToken(credentials, dataAccessCollectionIds, true));
        } catch (GlobusAuthException e) {
            log.info("Globus credential verification failed for client {}: {}", credentials.clientId(), e.getMessage());
            return false;
        }
    }

    /**
     * POST the client-credentials grant and return the raw JSON body. HTTP seam:
     * overridable in tests to avoid network access.
     *
     * @param credentials the client credentials
     * @param scope       the requested scope
     * @return the token response body
     * @throws GlobusAuthException if the request fails
     */
    protected String postTokenRequest(final GlobusCredentials credentials, final String scope) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(credentials));

        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);

        try {
            final ResponseEntity<String> response =
                    new RestTemplate().postForEntity(_tokenEndpoint, new HttpEntity<>(form, headers), String.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            // Surface Globus's own error body (e.g. invalid_client, unknown_scope_error);
            // without it, the cause is undiagnosable from the log.
            throw new GlobusAuthException("Globus token request failed for client " + credentials.clientId()
                    + " (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new GlobusAuthException("Globus token request failed for client " + credentials.clientId(), e);
        }
    }

    /**
     * Extract the Transfer access token and expiry from a token response,
     * checking {@code other_tokens} for multi-resource-server responses.
     *
     * @param json the token response body
     * @return the cached token (expiry already includes the safety margin)
     * @throws GlobusAuthException if no usable Transfer token is present
     */
    CachedToken parseTransferToken(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new GlobusAuthException("Empty Globus token response");
        }
        final JsonNode root;
        try {
            root = _objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new GlobusAuthException("Could not parse Globus token response", e);
        }

        JsonNode tokenNode = isTransferToken(root) ? root : null;
        if (tokenNode == null && root.has("other_tokens")) {
            for (final JsonNode candidate : root.get("other_tokens")) {
                if (isTransferToken(candidate)) {
                    tokenNode = candidate;
                    break;
                }
            }
        }
        if (tokenNode == null || !tokenNode.hasNonNull("access_token")) {
            throw new GlobusAuthException("Globus response contained no Transfer access token");
        }

        final String accessToken = tokenNode.get("access_token").asText();
        final long expiresInMs = tokenNode.path("expires_in").asLong(0L) * 1000L;
        final long expiresAt = System.currentTimeMillis() + Math.max(0L, expiresInMs - EXPIRY_SAFETY_MARGIN_MS);
        return new CachedToken(accessToken, expiresAt);
    }

    private static boolean isTransferToken(final JsonNode node) {
        return node != null && node.hasNonNull("resource_server")
                && TRANSFER_RESOURCE_SERVER.equals(node.get("resource_server").asText());
    }

    private static String basicAuth(final GlobusCredentials credentials) {
        final String raw = credentials.clientId() + ":" + credentials.clientSecret();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** A cached access token with a computed expiry (safety margin already applied). */
    record CachedToken(String accessToken, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
