package org.nrg.xsync.manifest.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;


/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Setter
@Entity
@Table(uniqueConstraints = {})
public class XsyncResourceHistory extends AbstractHibernateEntity {

    public XsyncResourceHistory() {}

    @Getter
    private String localLabel;
    @Getter
    private String subjectLabel;
    @Getter
    private String experimentLabel;
    @Getter
    private int fileCount;
    @Getter
    private Long fileSize;
    @Getter
    private String syncStatus;
    private String syncMessage;

    @Column(columnDefinition = "TEXT")
    public String getSyncMessage() {
        return syncMessage;
    }

}