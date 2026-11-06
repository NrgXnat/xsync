package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API carrier for a per-user Globus upload grant shown in the project's Globus
 * Upload tab. On input (add), only {@link #globusUsername} and
 * {@link #xnatUsername} are supplied; the rest are populated by the server.
 * Orphaned entries (a Globus ACL with no XNAT record) are returned with
 * {@code state = ORPHANED} and no stored record fields.
 *
 * @author XSync
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobusUploadGrantPojo {

    private String projectId;
    private String globusUsername;
    private String globusIdentityId;
    private String xnatUsername;
    private String permissions;
    private String state;
    private String globusAclId;
}
