package org.nrg.xsync.globus.entities;

/**
 * Reconciliation state of a {@link GlobusUploadGrant} relative to the live
 * Globus ACL on the upload collection.
 *
 * @author XSync
 */
public enum GlobusUploadGrantState {
    /** An XNAT record and a matching Globus ACL both exist. */
    ACTIVE,
    /** An XNAT record exists but no matching Globus ACL was found (or provisioning has not completed). */
    PENDING,
    /** Provisioning the Globus ACL failed; the record is retained so the admin can retry or remove it. */
    ERROR,
    /** A Globus ACL exists with no XNAT record (e.g. a hand-made CLI grant). Not persisted; computed during reconciliation. */
    ORPHANED
}
