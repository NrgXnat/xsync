package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API carrier for a configured Globus transfer destination: its inbox
 * collection and the paths a transfer writes to.
 *
 * @author XSync
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobusEndpointPojo {

    private String name;
    private String inboxCollectionId;
    private String remoteInboxPath;
    private String remoteInboxServerDirectory;
}
