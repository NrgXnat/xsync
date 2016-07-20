package org.nrg.xsync.manifest;

import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

import javax.persistence.Entity;
import javax.persistence.Table;


/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Entity
@Table(uniqueConstraints = {})
public class XsyncAssessorHistory extends AbstractHibernateEntity {

    public XsyncAssessorHistory() {}

    private String localLabel;
    private String subjectLabel;
    private String experimentLabel;
    private String syncStatus;
    private String syncMessage;

    public String getLocalLabel() {
        return localLabel;
    }

    public void setLocalLabel(String localLabel) {
        this.localLabel = localLabel;
    }

    public String getSubjectLabel() {
        return subjectLabel;
    }

    public void setSubjectLabel(String subjectLabel) {
        this.subjectLabel = subjectLabel;
    }

    public String getExperimentLabel() {
        return experimentLabel;
    }

    public void setExperimentLabel(String experimentLabel) {
        this.experimentLabel = experimentLabel;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getSyncMessage() {
        return syncMessage;
    }

    public void setSyncMessage(String syncMessage) {
        this.syncMessage = syncMessage;
    }
}