package org.nrg.xsync.globus.entities;

/**
 * Reconciliation state of a Globus ACL grant record (upload or sync peer)
 * relative to the live Globus ACL on its collection.
 *
 * @author XSync
 */
public enum GlobusGrantState {
    /** An XNAT record and a matching Globus ACL both exist. */
    ACTIVE,
    /** An XNAT record exists but no matching Globus ACL was found (or provisioning has not completed). */
    PENDING,
    /** A Globus ACL exists with no XNAT record (e.g. a hand-made CLI grant). Not persisted; computed during reconciliation. */
    ORPHANED
}
