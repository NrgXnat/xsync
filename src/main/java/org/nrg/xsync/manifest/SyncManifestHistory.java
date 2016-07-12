package org.nrg.xsync.manifest;

import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.util.*;

/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Entity
@Table(uniqueConstraints = {@UniqueConstraint(columnNames="startDate")})
public class SyncManifestHistory extends AbstractHibernateEntity {

    public Date getStartDate() {
        return startDate;
    }

    public Date getCompleteDate() {
        return completeDate;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public String getRemoteProject() {
        return remoteProject;
    }

    public int getSubjectCount() {
        return subjectCount;
    }

    public int getAssessorsCount() {
        return assessorsCount;
    }

    public int getResourcesCount() {
        return resourcesCount;
    }

    public String getTotalDataSynced() {
        return totalDataSynced;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

//    public List<Map<String, String>> getSubjectsSynced() {
//        return subjectsSynced;
//    }
//
//    public List<Map<String, String>> getExperimentsSynced() {
//        return experimentsSynced;
//    }
//
//    public List<Map<String, String>> getAssessorsSynced() {
//        return assessorsSynced;
//    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public void setCompleteDate(Date completeDate) {
        this.completeDate = completeDate;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setRemoteProject(String remoteProject) {
        this.remoteProject = remoteProject;
    }

    public void setSubjectCount(int subjectCount) {
        this.subjectCount = subjectCount;
    }

    public void setAssessorsCount(int assessorsCount) {
        this.assessorsCount = assessorsCount;
    }

    public void setResourcesCount(int resourcesCount) {
        this.resourcesCount = resourcesCount;
    }

    public void setTotalDataSynced(String totalDataSynced) {
        this.totalDataSynced = totalDataSynced;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

//    public void setSubjectsSynced(List<Map<String, String>> subjectsSynced) {
//        this.subjectsSynced = subjectsSynced;
//    }
//
//    public void setExperimentsSynced(List<Map<String, String>> experimentsSynced) {
//        this.experimentsSynced = experimentsSynced;
//    }
//
//    public void setAssessorsSynced(List<Map<String, String>> assessorsSynced) {
//        this.assessorsSynced = assessorsSynced;
//    }

    private Date startDate = new Date();
    private Date completeDate = new Date();
    private String remoteHost;
    private String remoteProject;
    private int subjectCount = 0;
    private int assessorsCount = 0;
    private int resourcesCount= 0;
    private String totalDataSynced;
    private String syncStatus;
//    private List<Map<String, String>> subjectsSynced = new ArrayList<>();
//    private List<Map<String, String>> experimentsSynced = new ArrayList<>();
//    private List<Map<String, String>> assessorsSynced = new ArrayList<>();
}
