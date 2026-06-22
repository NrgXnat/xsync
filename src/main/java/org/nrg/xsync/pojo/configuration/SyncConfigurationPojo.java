package org.nrg.xsync.pojo.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SyncConfigurationPojo {

    private Boolean enabled;
    private String source_project_id;
    private String sync_frequency;
    private Boolean sync_new_only;
    private String identifiers;
    private String remote_url;
    private String remote_project_id;
    private String notification_emails;
    private String customIdentifiers;
    private Boolean anonymize;
    private Integer no_of_retry_days;
    private SyncConfigurationResourcePojo project_resources;
    private SyncConfigurationResourcePojo subject_resources;
    private SyncConfigurationAssessorPojo subject_assessors;
    private SyncConfigurationAssessorPojo imaging_sessions;
}
