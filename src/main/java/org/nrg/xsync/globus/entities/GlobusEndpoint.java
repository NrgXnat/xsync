package org.nrg.xsync.globus.entities;

import javax.persistence.Column;
import javax.persistence.Entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * A registered Globus transfer destination — a peer this node sends to: the
 * peer's inbox collection and the paths a transfer writes to. The node's own
 * service account and outbox are held in {@link GlobusNodeConfig}.
 *
 * @author XSync
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GlobusEndpoint extends AbstractHibernateEntity {

    /** Human-friendly, unique name for this destination. */
    @Column(unique = true, nullable = false)
    private String name;

    /** UUID of the destination's inbox guest collection, where this node writes. */
    @Column(nullable = false)
    private String inboxCollectionId;

    /**
     * Collection-relative path within the destination inbox that this node
     * writes to (this node's subpath, e.g. {@code /site1}).
     */
    private String remoteInboxPath;

    /**
     * Destination host's local filesystem directory where the transferred XAR
     * lands, passed to the import-by-path call.
     */
    private String remoteInboxServerDirectory;
}
