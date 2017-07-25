package org.nrg.xsync.manifest;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;
import org.springframework.transaction.annotation.Transactional;


/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Entity
@Table(uniqueConstraints = {})
public class XsyncSubjectHistory extends AbstractHibernateEntity {

    public XsyncSubjectHistory() {}

    private String localLabel;
    private String syncStatus;
    private String syncMessage;


    public String getLocalLabel() {
        return localLabel;
    }

    @Transactional
    public void setLocalLabel(String localLabel) {
        this.localLabel = localLabel;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    @Transactional
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    @Column(columnDefinition = "TEXT")
    public String getSyncMessage() {
        return syncMessage;
    }

    @Transactional
    public void setSyncMessage(String syncMessage) {
        this.syncMessage = syncMessage;
    }

//    @ManyToOne(cascade= CascadeType.ALL)
//    public XsyncProjectHistory getProjectHistory() {
//        return projectHistory;
//    }
//    public void persistHistory(XsyncProjectHistory projectHistory) {
//        this.projectHistory = projectHistory;
//    }
}