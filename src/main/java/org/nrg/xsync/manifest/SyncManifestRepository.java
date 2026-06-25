package org.nrg.xsync.manifest;

import org.hibernate.SQLQuery;
import org.nrg.framework.orm.hibernate.AbstractHibernateDAO;
import org.nrg.xsync.manifest.history.XsyncAssessorHistory;
import org.nrg.xsync.manifest.history.XsyncExperimentHistory;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;
import org.nrg.xsync.manifest.history.XsyncResourceHistory;
import org.nrg.xsync.manifest.history.XsyncSubjectHistory;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Michael Hileman on 2016/07/07.
 */
@Repository
public class SyncManifestRepository extends AbstractHibernateDAO<XsyncProjectHistory> {


    @Nullable
    public XsyncProjectHistory findMostRecentBySubject(final String projectId, final String subjectLabel) {
        final String queryStr = SUBJECT_QUERY + " where subj.local_label = '" + subjectLabel + "' and proj.local_project='" + projectId + "' order by proj.start_date desc;";
        SQLQuery query = getSession().createSQLQuery(queryStr);
        //query.setResultTransformer(Transformers.aliasToBean(XsyncProjectHistory.class));
        List<BigInteger> rows = query.list();
        if (rows != null && rows.size() > 0) {
            //MR - We have selected only the IDs of the table, hence query again to get all other details;
            //Hacky - no better solution found!
            BigInteger id = rows.get(0);
            XsyncProjectHistory projectHistory = retrieve(id.longValue());
            XsyncProjectHistory filteredProjectHistory = new XsyncProjectHistory();
            filteredProjectHistory.setId(projectHistory.getId());
            filteredProjectHistory.setRemoteHost(projectHistory.getRemoteHost());
            filteredProjectHistory.setLocalProject(projectHistory.getLocalProject());
            filteredProjectHistory.setRemoteProject(projectHistory.getRemoteProject());
            filteredProjectHistory.setSyncStatus(projectHistory.getSyncStatus());
            filteredProjectHistory.setStartDate(projectHistory.getStartDate());
            filteredProjectHistory.setCompleteDate(projectHistory.getCompleteDate());
            filteredProjectHistory.setSyncUser(projectHistory.getSyncUser());
            filteredProjectHistory.setSubjectHistories(new ArrayList<XsyncSubjectHistory>());
            filteredProjectHistory.setExperimentHistories(new ArrayList<XsyncExperimentHistory>());
            filteredProjectHistory.setAssessorHistories(new ArrayList<XsyncAssessorHistory>());
            filteredProjectHistory.setResourceHistories(new ArrayList<XsyncResourceHistory>());
            for (XsyncSubjectHistory s:projectHistory.getSubjectHistories()) {
                if (s.getLocalLabel() != null && s.getLocalLabel().equals(subjectLabel)) {
                    XsyncSubjectHistory subjectHistory = new XsyncSubjectHistory();
                    subjectHistory.setLocalLabel(s.getLocalLabel());
                    subjectHistory.setSyncStatus(s.getSyncStatus());
                    subjectHistory.setSyncMessage(s.getSyncMessage());
                    subjectHistory.setId(s.getId());
                    subjectHistory.setCreated(s.getCreated());
                    filteredProjectHistory.getSubjectHistories().add(subjectHistory);
                }
            }
            for (XsyncExperimentHistory s:projectHistory.getExperimentHistories()) {
                if (s.getSubjectLabel() != null && s.getSubjectLabel().equals(subjectLabel)) {
                    XsyncExperimentHistory experimentHistory = new XsyncExperimentHistory();
                    experimentHistory.setLocalLabel(s.getLocalLabel());
                    experimentHistory.setSubjectLabel(s.getSubjectLabel());
                    experimentHistory.setSyncStatus(s.getSyncStatus());
                    experimentHistory.setSyncMessage(s.getSyncMessage());
                    experimentHistory.setId(s.getId());
                    experimentHistory.setCreated(s.getCreated());
                    filteredProjectHistory.getExperimentHistories().add(experimentHistory);
                }
            }
            for (XsyncAssessorHistory s:projectHistory.getAssessorHistories()) {
                if (s.getSubjectLabel() != null && s.getSubjectLabel().equals(subjectLabel)) {
                    XsyncAssessorHistory assessorHistory = new XsyncAssessorHistory();
                    assessorHistory.setLocalLabel(s.getLocalLabel());
                    assessorHistory.setSubjectLabel(s.getSubjectLabel());
                    assessorHistory.setExperimentLabel(s.getExperimentLabel());
                    assessorHistory.setSyncStatus(s.getSyncStatus());
                    assessorHistory.setSyncMessage(s.getSyncMessage());
                    assessorHistory.setId(s.getId());
                    assessorHistory.setCreated(s.getCreated());
                    filteredProjectHistory.getAssessorHistories().add(assessorHistory);
                }
            }
            for (XsyncResourceHistory s:projectHistory.getResourceHistories()) {
                if (s.getSubjectLabel() != null && s.getSubjectLabel().equals(subjectLabel)) {
                    XsyncResourceHistory resourceHistory = new XsyncResourceHistory();
                    resourceHistory.setLocalLabel(s.getLocalLabel());
                    resourceHistory.setSubjectLabel(s.getSubjectLabel());
                    resourceHistory.setExperimentLabel(s.getExperimentLabel());
                    resourceHistory.setSyncStatus(s.getSyncStatus());
                    resourceHistory.setSyncMessage(s.getSyncMessage());
                    resourceHistory.setId(s.getId());
                    resourceHistory.setCreated(s.getCreated());
                    resourceHistory.setFileCount(s.getFileCount());
                    resourceHistory.setFileSize(s.getFileSize());
                    filteredProjectHistory.getResourceHistories().add(resourceHistory);
                }
            }

            return filteredProjectHistory;
        }
        return null;
    }



    final private String SUBJECT_QUERY = "select proj.id as id from xhbm_xsync_project_history proj " +
    " inner join xhbm_xsync_project_history_subject_histories subj_proj on subj_proj.xsync_project_history=proj.id " +
    " inner join xhbm_xsync_subject_history subj ON subj.id = subj_proj.subject_histories " +
    " left join xhbm_xsync_project_history_assessor_histories ah on proj.id=ah.xsync_project_history " +
    " left join xhbm_xsync_assessor_history assh on ah.assessor_histories=assh.id " +
    " left join xhbm_xsync_project_history_experiment_histories eh on proj.id=eh.xsync_project_history " +
    " left join xhbm_xsync_experiment_history exph on eh.experiment_histories=exph.id " +
    " left join xhbm_xsync_project_history_resource_histories resh on proj.id=ah.xsync_project_history " +
    " left join xhbm_xsync_resource_history rh on resh.resource_histories=rh.id " ;


}
