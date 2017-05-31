package org.nrg.xsync.manifest;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Entity
@Table(uniqueConstraints = {@UniqueConstraint(columnNames="startDate")})
public class XsyncProjectHistory extends AbstractHibernateEntity {

    public XsyncProjectHistory() {}

    private Date startDate = new Date();
    private Date completeDate = new Date();
    private String remoteHost;
    private String remoteProject;
    private String localProject;
    private String syncStatus;
    private String syncUser;
    private int totalSubjects = 0;
    private int totalExperiments = 0;
    private int totalAssessors = 0;
    private int totalResources = 0;
    private String totalDataSynced;
    private List<XsyncSubjectHistory> subjectHistories;
    private List<XsyncExperimentHistory> experimentHistories;
    private List<XsyncAssessorHistory> assessorHistories;
    private List<XsyncResourceHistory> resourceHistories;

    @OneToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
    @Fetch(value = FetchMode.SUBSELECT)
    public List<XsyncExperimentHistory> getExperimentHistories() {
        return experimentHistories;
    }
    public void setExperimentHistories(List<XsyncExperimentHistory> experimentHistories) {
        this.experimentHistories = experimentHistories;
    }

    @OneToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
    @Fetch(value = FetchMode.SUBSELECT)
    public List<XsyncSubjectHistory> getSubjectHistories() {
        return subjectHistories;
    }
    public void setSubjectHistories(List<XsyncSubjectHistory> assessorHistories) {
        this.subjectHistories = assessorHistories;
    }

    @OneToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
    @Fetch(value = FetchMode.SUBSELECT)
    public List<XsyncAssessorHistory> getAssessorHistories() {
        return assessorHistories;
    }
    public void setAssessorHistories(List<XsyncAssessorHistory> assessorHistories) {
        this.assessorHistories = assessorHistories;
    }

    @OneToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
    @Fetch(value = FetchMode.SUBSELECT)
    public List<XsyncResourceHistory> getResourceHistories() {
        return resourceHistories;
    }
    public void setResourceHistories(List<XsyncResourceHistory> resourceHistories) {
        this.resourceHistories = resourceHistories;
    }

    public int getTotalExperiments() {
        return totalExperiments;
    }

    public void setTotalExperiments(int totalExperiments) {
        this.totalExperiments = totalExperiments;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getCompleteDate() {
        return completeDate;
    }

    public void setCompleteDate(Date completeDate) {
        this.completeDate = completeDate;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public String getRemoteProject() {
        return remoteProject;
    }

    public void setRemoteProject(String remoteProject) {
        this.remoteProject = remoteProject;
    }

    public String getLocalProject() {
        return localProject;
    }

    public void setLocalProject(String localProject) {
        this.localProject = localProject;
    }

    public int getTotalSubjects() {
        return totalSubjects;
    }

    public void setTotalSubjects(int totalSubjects) {
        this.totalSubjects = totalSubjects;
    }

    public int getTotalAssessors() {
        return totalAssessors;
    }

    public void setTotalAssessors(int totalAssessors) {
        this.totalAssessors = totalAssessors;
    }

    public int getTotalResources() {
        return totalResources;
    }

    public void setTotalResources(int totalResources) {
        this.totalResources = totalResources;
    }

    public String getTotalDataSynced() {
        return totalDataSynced;
    }

    public void setTotalDataSynced(String totalDataSynced) {
        this.totalDataSynced = totalDataSynced;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getSyncUser() {
        return syncUser;
    }

    public void setSyncUser(String syncUser) {
        this.syncUser = syncUser;
    }
}