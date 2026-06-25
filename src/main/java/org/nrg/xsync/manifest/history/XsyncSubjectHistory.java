package org.nrg.xsync.manifest.history;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Getter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;
import org.springframework.transaction.annotation.Transactional;


/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Entity
@Table()
public class XsyncSubjectHistory extends AbstractHibernateEntity {

    public XsyncSubjectHistory() {}

    @Getter
    private String localLabel;
    @Getter
    private String syncStatus;
    private String syncMessage;

    @Transactional
    public void setLocalLabel(String localLabel) {
        this.localLabel = localLabel;
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

}