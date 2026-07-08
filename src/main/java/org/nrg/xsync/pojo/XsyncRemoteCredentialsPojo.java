package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class XsyncRemoteCredentialsPojo {
    private String host;
    private String localProject;
    private String remoteProject;
    private Boolean syncNewOnly;
    private String alias;
    private String secret;
    private String username;
    private String estimatedExpirationTime;
}
