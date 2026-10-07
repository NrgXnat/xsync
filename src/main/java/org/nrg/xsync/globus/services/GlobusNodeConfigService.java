package org.nrg.xsync.globus.services;

import java.util.Optional;

import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.GlobusCredentials;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;

/**
 * Stores and retrieves this node's single Globus configuration (service account
 * + outbox), and resolves the {@link GlobusCredentials} used for transfers.
 *
 * <p>This is the single point where service credentials are resolved: all
 * callers obtain them through {@link #getServiceCredentials()}, so any change
 * to credential policy stays localized to this one method.</p>
 *
 * @author XSync
 */
public interface GlobusNodeConfigService {

    /** @return the node's Globus configuration, or empty if not yet configured. */
    Optional<GlobusNodeConfig> getConfig();

    /**
     * Create or update the node's single Globus configuration. A blank secret on
     * update leaves the stored secret unchanged.
     *
     * @param config the configuration to persist
     * @return the persisted configuration
     * @throws DataFormatException if required fields are missing, or a secret is
     *                             absent when first configuring
     */
    GlobusNodeConfig save(GlobusNodeConfig config) throws DataFormatException;

    /**
     * @return this node's service-account credentials
     * @throws NotFoundException if the node's Globus configuration is not set
     */
    GlobusCredentials getServiceCredentials() throws NotFoundException;
}
