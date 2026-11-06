package org.nrg.xsync.globus;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.services.SerializerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Resolves Globus identities via the Globus Auth
 * <a href="https://docs.globus.org/api/auth/reference/">Get Identities</a>
 * endpoint ({@code GET /v2/api/identities}): username &rarr; UUID when adding a
 * grant, and UUID &rarr; username when displaying a Globus ACL that has no XNAT
 * record (design {@code XSYNC_GLOBUS_UPLOAD_ACL_UI_DESIGN.md} §6).
 *
 * <p>The lookup requires no special OAuth scope ("available with any
 * authentication/client type"), so this calls the endpoint with HTTP Basic
 * authentication using the node's confidential-client credentials, avoiding any
 * token-audience question. Whether that returns the needed fields is the §10
 * build-time confirmation; the HTTP call is isolated in {@link #get(String,
 * GlobusCredentials)} so it can be exercised in tests without network access.</p>
 *
 * <p>Username lookups pass {@code provision=false}: the Globus default is
 * {@code true}, which would auto-create an identity id for an unknown username
 * and so silently accept a typo. With {@code provision=false} an unknown
 * username resolves to {@link Optional#empty()} and the caller fails cleanly.</p>
 *
 * @author XSync
 */
@Service
@Slf4j
public class GlobusIdentityService {

    /** Globus Auth Get Identities endpoint. */
    public static final String IDENTITIES_ENDPOINT = "https://auth.globus.org/v2/api/identities";

    private final ObjectMapper _objectMapper;

    @Autowired
    public GlobusIdentityService(final SerializerService serializerService) {
        this(serializerService.getObjectMapper());
    }

    /** Constructor for tests (supply an {@link ObjectMapper} directly). */
    GlobusIdentityService(final ObjectMapper objectMapper) {
        _objectMapper = objectMapper;
    }

    /** A resolved Globus identity. */
    public record GlobusIdentity(String id, String username) {
    }

    /**
     * Resolve a Globus username (or email) to its identity, without provisioning
     * a new one.
     *
     * @param credentials the confidential-client credentials used for Basic auth
     * @param username    the Globus username/email to look up
     * @return the identity if it exists, else empty
     * @throws GlobusAuthException if the lookup request fails
     */
    public Optional<GlobusIdentity> resolveByUsername(final GlobusCredentials credentials, final String username) {
        final String url = UriComponentsBuilder.fromHttpUrl(IDENTITIES_ENDPOINT)
                .queryParam("usernames", username)
                .queryParam("provision", "false")
                .toUriString();
        return firstIdentity(get(url, credentials));
    }

    /**
     * Resolve a Globus identity UUID back to its username (for displaying an ACL
     * that has no XNAT record).
     *
     * @param credentials the confidential-client credentials used for Basic auth
     * @param id          the Globus identity UUID
     * @return the identity if it exists, else empty
     * @throws GlobusAuthException if the lookup request fails
     */
    public Optional<GlobusIdentity> resolveById(final GlobusCredentials credentials, final String id) {
        final String url = UriComponentsBuilder.fromHttpUrl(IDENTITIES_ENDPOINT)
                .queryParam("ids", id)
                .toUriString();
        return firstIdentity(get(url, credentials));
    }

    /**
     * Parse the first usable identity (non-blank id) from an identities response.
     *
     * @param json the {@code {"identities":[...]}} response body
     * @return the first identity with a non-blank id, if any
     */
    private Optional<GlobusIdentity> firstIdentity(final String json) {
        if (StringUtils.isBlank(json)) {
            return Optional.empty();
        }
        try {
            for (final JsonNode node : _objectMapper.readTree(json).path("identities")) {
                final String id = node.path("id").asText(null);
                if (StringUtils.isNotBlank(id)) {
                    return Optional.of(new GlobusIdentity(id, node.path("username").asText(null)));
                }
            }
            return Optional.empty();
        } catch (JsonProcessingException e) {
            throw new GlobusAuthException("Could not parse Globus identities response", e);
        }
    }

    /**
     * Issue the Get Identities GET with Basic client authentication. HTTP seam:
     * overridable in tests to avoid network access.
     *
     * @param url         the fully-formed identities URL
     * @param credentials the confidential-client credentials
     * @return the response body
     * @throws GlobusAuthException if the request fails
     */
    protected String get(final String url, final GlobusCredentials credentials) {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(credentials));
        try {
            return new RestTemplate()
                    .exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), String.class)
                    .getBody();
        } catch (HttpStatusCodeException e) {
            throw new GlobusAuthException("Globus identity lookup failed (HTTP " + e.getStatusCode() + "): "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new GlobusAuthException("Globus identity lookup failed", e);
        }
    }

    private static String basicAuth(final GlobusCredentials credentials) {
        final String raw = credentials.clientId() + ":" + credentials.clientSecret();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
