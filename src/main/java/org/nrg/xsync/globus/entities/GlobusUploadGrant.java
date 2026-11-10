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
 * The XNAT-owned record binding a Globus identity to the XNAT user whose
 * uploads it may submit, for one project. It is the authoritative record of a
 * per-user upload grant; the matching Globus ACL on the upload collection is
 * provisioned from it.
 *
 * <p>The durable key is {@link #globusIdentityId} (a Globus identity UUID), not
 * the username: an identity provider may reuse a username over time, so the UUID
 * is what the Globus ACL's {@code principal} is matched against during
 * reconciliation. {@link #globusUsername} is display only.</p>
 *
 * <p>Uniqueness of {@code (projectId, globusIdentityId)} is enforced in the
 * service layer ({@code GlobusUploadPermissionManager.add} rejects a duplicate
 * before inserting), not by a database constraint: a JPA {@code @UniqueConstraint}
 * here must name columns by XNAT's naming strategy, which is brittle and has
 * failed session-factory initialization, so it is deliberately omitted.</p>
 *
 * @author XSync
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GlobusUploadGrant extends AbstractHibernateEntity {

    /** XNAT project this grant belongs to. */
    @Column(nullable = false)
    private String projectId;

    /** The Globus identity's username/email as entered by the admin (display only). */
    private String globusUsername;

    /** The Globus identity UUID (the durable key; matched against the ACL {@code principal}). */
    @Column(nullable = false)
    private String globusIdentityId;

    /** The XNAT user whose permissions the uploaded studies import under. */
    @Column(nullable = false)
    private String xnatUsername;

    /** The collection-relative subpath granted, {@code /<projectId>/<xnatUsername>/} (derived). */
    private String subpath;

    /** The Globus access-rule id returned when the ACL was provisioned; null until provisioned. */
    private String globusAclId;

    /** Reconciliation state relative to the live Globus ACL. */
    @Enumerated(EnumType.STRING)
    private GlobusUploadGrantState state;
}
