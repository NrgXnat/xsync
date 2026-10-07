package org.nrg.xsync.globus.entities;

import javax.persistence.Entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * This node's single Globus configuration: the one confidential-client service
 * account and the one outbox the node transfers from. A node has exactly one of
 * these (a singleton row); per-destination routing lives in {@link GlobusEndpoint}.
 *
 * <p>The {@link #clientSecret} is stored <strong>in plaintext for now</strong>;
 * at-rest encryption is a planned follow-up — see {@code GLOBUS_TRANSFER_PLAN.md}
 * §6.8.</p>
 *
 * @author XSync
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GlobusNodeConfig extends AbstractHibernateEntity {

    /** Globus confidential-client (application) id — this node's service account. */
    private String clientId;

    /** Globus confidential-client secret. Plaintext for now (see §6.8). */
    private String clientSecret;

    /** UUID of this node's outbox collection, which XSync sends staged XARs from. */
    private String outboxCollectionId;

    /** Local filesystem directory backing the outbox collection, where XARs are staged. */
    private String outboxDirectory;

    /**
     * UUID of this node's own inbox collection (where peers send to it). Not used
     * when sending; held for display/sharing with peers. Optional.
     */
    private String inboxCollectionId;
}
