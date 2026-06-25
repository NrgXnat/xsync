package org.nrg.xsync.pojo.history;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nrg.xsync.manifest.XsyncAssessorHistory;
import org.nrg.xsync.manifest.XsyncExperimentHistory;
import org.nrg.xsync.manifest.XsyncResourceHistory;
import org.nrg.xsync.manifest.XsyncSubjectHistory;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XsyncProjectHistoryPojo {
    private Date startDate;
    private Date completeDate;
    private String remoteHost;
    private String remoteProject;
    private String localProject;
    private String syncStatus;
    private String syncUser;
    private int totalSubjects;
    private int totalExperiments;
    private int totalAssessors;
    private int totalResources;
    private String totalDataSynced;
    private List<XsyncSubjectHistoryPojo> subjectHistories;
    private List<XsyncExperimentHistoryPojo> experimentHistories;
    private List<XsyncAssessorHistoryPojo> assessorHistories;
    private List<XsyncResourceHistoryPojo> resourceHistories;
    private long id;
}
