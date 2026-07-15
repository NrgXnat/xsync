package org.nrg.xsync.pojo.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SyncConfigurationAssessorPojo {
    private String sync_type;
    private List<String> xsi_types;
}
