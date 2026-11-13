package org.nrg.xsync.globus.services;

import java.util.List;
import java.util.Optional;

import org.nrg.xsync.globus.entities.GlobusSyncPeerGrant;

/**
 * Persistence for {@link GlobusSyncPeerGrant} records — the site-level bindings
 * of peer Globus clients to this node's sync inbox. Provisioning the matching
 * Globus ACL is orchestrated by
 * {@link org.nrg.xsync.globus.sync.GlobusSyncPeerGrantManager}; this service only
 * stores and retrieves the records.
 *
 * @author XSync
 */
public interface GlobusSyncPeerGrantService {

    /** @return all peer grants on this node's sync inbox. */
    List<GlobusSyncPeerGrant> getAll();

    /**
     * @param peerIdentityId the peer client's Globus identity UUID
     * @return the grant for that peer, if present
     */
    Optional<GlobusSyncPeerGrant> getByPeerIdentityId(String peerIdentityId);

    /**
     * Persist (create or update) a peer grant record.
     *
     * @param grant the record to save
     * @return the saved record
     */
    GlobusSyncPeerGrant save(GlobusSyncPeerGrant grant);

    /**
     * Delete a peer grant record.
     *
     * @param grant the record to delete
     */
    void delete(GlobusSyncPeerGrant grant);
}
