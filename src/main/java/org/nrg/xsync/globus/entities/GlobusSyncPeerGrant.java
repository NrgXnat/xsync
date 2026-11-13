package org.nrg.xsync.globus.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * The XNAT-owned record authorizing a peer node's Globus client to write to this
 * node's sync inbox — the site-level counterpart to {@link GlobusUploadGrant}.
 * One row per peer: the peer's confidential-client identity is granted {@code rw}
 * on its own subpath ({@code /<peerLabel>/}) of the sync inbox guest collection.
 *
 * <p>The durable key and ACL principal is {@link #peerIdentityId}, which for a
 * confidential client equals its client id (verified against the Globus
 * identities endpoint). {@link #peerLabel} is the human-friendly display and the
 * subpath segment. Uniqueness of {@code peerIdentityId} is a single-column
 * constraint (not a cross-column {@code @UniqueConstraint}, which must name
 * physical columns and can fail session-factory init).</p>
 *
 * @author XSync
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GlobusSyncPeerGrant extends AbstractHibernateEntity {

    /** Human-friendly peer name; also the inbox subpath segment (e.g. {@code site1}). */
    @Column(nullable = false)
    private String peerLabel;

    /** The peer confidential client's Globus identity UUID (= its client id); the ACL principal. */
    @Column(unique = true, nullable = false)
    private String peerIdentityId;

    /** The collection-relative subpath granted, {@code /<peerLabel>/} (derived). */
    private String subpath;

    /** The Globus access-rule id returned when the ACL was provisioned; null until provisioned. */
    private String globusAclId;

    /** Reconciliation state relative to the live Globus ACL. */
    @Enumerated(EnumType.STRING)
    private GlobusGrantState state;
}
