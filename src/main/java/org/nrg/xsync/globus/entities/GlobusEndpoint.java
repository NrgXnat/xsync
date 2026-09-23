package org.nrg.xsync.globus.entities;

import javax.persistence.Column;
import javax.persistence.Entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * A configured Globus endpoint that XSync can transfer to/from, together with
 * the confidential-client credentials used to authenticate to it.
 *
 * <p>One row per endpoint supports the requirement that a site configure
 * multiple endpoints with possibly different credentials. The
 * {@link #clientSecret} is stored <strong>in plaintext for now</strong>;
 * at-rest encryption (and the migration of existing secrets) is a planned
 * follow-up&mdash;see {@code GLOBUS_TRANSFER_PLAN.md}, "Secret encryption."</p>
 *
 * @author XSync
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GlobusEndpoint extends AbstractHibernateEntity {

    /** Human-friendly, unique name for this endpoint configuration. */
    @Column(unique = true, nullable = false)
    private String name;

    /** Globus confidential-client (application) id. */
    @Column(nullable = false)
    private String clientId;

    /**
     * Globus confidential-client secret.
     *
     * <p>Plaintext for now; see {@code GLOBUS_TRANSFER_PLAN.md} for the planned
     * at-rest encryption.</p>
     */
    @Column(nullable = false)
    private String clientSecret;

    /**
     * UUID of this node's <strong>inbox</strong> collection (where XARs/DICOM
     * from remote nodes land and are auto-imported). Nullable: a send-only node
     * may have no inbox.
     */
    private String inboxCollectionId;

    /**
     * UUID of this node's <strong>outbox</strong> collection (which XSync sends
     * staged XARs from). Nullable: a receive-only (hub) node may have no outbox.
     */
    private String outboxCollectionId;
}
