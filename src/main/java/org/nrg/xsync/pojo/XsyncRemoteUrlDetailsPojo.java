package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XsyncRemoteUrlDetailsPojo {
    private String remoteUrl;
    private String siteName;
    private String classification;
    private int numberProjects;
    private int numberErrors;

}
