package org.nrg.xsync.globus;

/**
 * Credentials for a Globus confidential client (the OAuth2 client-credentials
 * grant XSync uses to drive Globus transfers unattended).
 *
 * <p>This is a storage-agnostic carrier: whatever persists Globus endpoint
 * configuration (a Hibernate entity, an encrypted preference, etc.) produces
 * one of these for {@link GlobusAuthService} to consume. The secret is never
 * included in {@link #toString()}.</p>
 *
 * @param clientId     the Globus application (client) id
 * @param clientSecret the client secret
 *
 * @author XSync
 */
public record GlobusCredentials(String clientId, String clientSecret) {

    @Override
    public String toString() {
        return "GlobusCredentials[clientId=" + clientId + ", clientSecret=***]";
    }
}
