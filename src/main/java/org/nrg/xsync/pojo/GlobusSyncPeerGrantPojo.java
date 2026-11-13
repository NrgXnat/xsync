package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API carrier for a sync-inbox peer grant shown in the site-level admin UI. On
 * input (add), only {@link #peerLabel} and {@link #peerClientId} are supplied;
 * the rest are populated by the server. Orphaned entries (a Globus ACL with no
 * XNAT record) are returned with {@code state = ORPHANED}. {@link #peerClientId}
 * carries the peer client's id, which equals its identity UUID (the ACL
 * principal).
 *
 * @author XSync
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobusSyncPeerGrantPojo {

    private String peerLabel;
    private String peerClientId;
    private String subpath;
    private String permissions;
    private String state;
    private String globusAclId;
}
