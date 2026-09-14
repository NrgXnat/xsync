package org.nrg.xsync.manifest.history;

import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * Created by Michael Hileman on 2016/07/06.
 */

@Setter
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(columnNames="startDate")})
public class XsyncProjectHistory extends AbstractHibernateEntity {

    public XsyncProjectHistory() {}

    @Getter
    private Date startDate = new Date();
    @Getter
    private Date completeDate = new Date();
    @Getter
    private String remoteHost;
    @Getter
    private String remoteProject;
    @Getter
    private String localProject;
    @Getter
    private String syncStatus;
    @Getter
    private String syncUser;
    @Getter
    private int totalSubjects = 0;
    @Getter
    private int totalExperiments = 0;
    @Getter
    private int totalAssessors = 0;
    @Getter
    private int totalResources = 0;
    @Getter
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

    @OneToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
    @Fetch(value = FetchMode.SUBSELECT)
    public List<XsyncSubjectHistory> getSubjectHistories() {
        return subjectHistories;
    }

    @OneToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
    @Fetch(value = FetchMode.SUBSELECT)
    public List<XsyncAssessorHistory> getAssessorHistories() {
        return assessorHistories;
    }

    @OneToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
    @Fetch(value = FetchMode.SUBSELECT)
    public List<XsyncResourceHistory> getResourceHistories() {
        return resourceHistories;
    }

}