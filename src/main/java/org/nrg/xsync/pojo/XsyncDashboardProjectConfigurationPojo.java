package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class XsyncDashboardProjectConfigurationPojo {
    private String localProject;
    private String remoteProject;
    private String status;
    private String frequency;
    private String lastSyncStatus;
}
