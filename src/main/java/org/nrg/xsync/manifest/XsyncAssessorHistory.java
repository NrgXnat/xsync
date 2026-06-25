package org.nrg.xsync.manifest;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;


/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Setter
@Entity
@Table(uniqueConstraints = {})
public class XsyncAssessorHistory extends AbstractHibernateEntity {

    public XsyncAssessorHistory() {}

    @Getter
    private String localLabel;
    @Getter
    private String subjectLabel;
    @Getter
    private String experimentLabel;
    @Getter
    private String syncStatus;
    private String syncMessage;

    @Column(columnDefinition = "TEXT")
    public String getSyncMessage() {
        return syncMessage;
    }

}