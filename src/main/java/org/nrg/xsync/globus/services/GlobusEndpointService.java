package org.nrg.xsync.globus.services;

import java.util.List;

import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.entities.GlobusEndpoint;

/**
 * Stores and retrieves configured Globus transfer destinations
 * ({@link GlobusEndpoint}). Service-account credentials are managed by
 * {@link org.nrg.xsync.globus.services.GlobusNodeConfigService}.
 *
 * @author XSync
 */
public interface GlobusEndpointService {

    /**
     * @return all configured Globus endpoints
     */
    List<GlobusEndpoint> getAllEndpoints();

    /**
     * @param name the endpoint name
     * @return the named endpoint
     * @throws NotFoundException if no endpoint has that name
     */
    GlobusEndpoint getByName(String name) throws NotFoundException;

    /**
     * Create a new endpoint or update the existing one with the same name. On
     * update, a blank secret leaves the stored secret unchanged.
     *
     * @param endpoint the endpoint to persist
     * @return the persisted endpoint
     * @throws DataFormatException if required fields are missing, or a secret is
     *                             absent when creating a new endpoint
     */
    GlobusEndpoint createOrUpdate(GlobusEndpoint endpoint) throws DataFormatException;

    /**
     * Delete the named endpoint.
     *
     * @param name the endpoint name
     * @throws NotFoundException if no endpoint has that name
     */
    void delete(String name) throws NotFoundException;
}
