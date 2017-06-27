package org.nrg.xsync.services.local.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.io.FileUtils;
import org.hibernate.HibernateException;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xsync.manifest.*;
import org.nrg.xsync.services.local.SyncManifestService;
import org.nrg.xsync.utils.XsyncFileUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Created by Michael Hileman on 2016/07/07.
 *
 */
@Service
@JsonIgnoreProperties(value = { "created" })
public class HibernateSyncHistoryService
        extends AbstractHibernateEntityService<XsyncProjectHistory, SyncManifestRepository>
        implements SyncManifestService {

    private final static Logger logger = LoggerFactory.getLogger(HibernateSyncHistoryService.class);

    private XsyncProjectHistory syncHistory = new XsyncProjectHistory();
    private SyncManifest manifest = null;

    @Transactional
    @Override
    public XsyncProjectHistory findByStartDate(final Date date) {
        return getDao().findByUniqueProperty("startDate", date);
    }

    @Transactional
    @Override
    public List<XsyncProjectHistory> findBySyncStatus(final String status) {
        return new ArrayList<>();
    }

    @Transactional
    @Override
    public List<XsyncProjectHistory> findBySubject(final String subjectLabel) {
        return new ArrayList<>();
    }

    @Transactional
    public synchronized void persistHistory(SyncManifest manifest) {
        this.manifest = manifest;
        this.setProjectHistory();
        this.setSubjectHistory();
        this.setExperiemntHistory();
        this.setAssessorHistory();
        this.setResourceHistory();
        try {
            this.create(syncHistory);
        } catch (HibernateException e) {
            logger.error("Unable to create XSync history entry from manifest - " + syncHistory.toString(), e);
        }
    }

    private void setProjectHistory() {
        String syncStatus = "Complete";
        if (!manifest.wasSyncSuccessfull()) {
            syncStatus = "Fail";
        } 
        String overAllSyncStatus = manifest.getOverAllSyncStatusWhenSucessfull();
        if (overAllSyncStatus.equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
        	syncStatus += " [Verified]";
        }else if (overAllSyncStatus.equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED)) {
        	syncStatus += " [NOT Verified]";
        }
        syncHistory.setSyncStatus(syncStatus);
        syncHistory.setStartDate(manifest.getSync_start_time());
        syncHistory.setCompleteDate(manifest.getSync_end_time());
        syncHistory.setRemoteHost(manifest.getSyncHost());
        syncHistory.setRemoteProject(manifest.getRemoteProjectId());
        syncHistory.setLocalProject(manifest.getLocalProjectId());
        syncHistory.setTotalSubjects(manifest.getSubjects().size());
        syncHistory.setTotalExperiments(countAllExperiments());
        syncHistory.setTotalAssessors(countAllAssessors());
        syncHistory.setTotalResources(countAllResources());
        syncHistory.setTotalDataSynced(calculateTotalData());
        syncHistory.setSyncUser(manifest.getSync_user().getUsername());
    }

    private void setSubjectHistory() {
        List<XsyncSubjectHistory> histories = new ArrayList<>();

        for (SubjectSyncItem sub : manifest.getSubjects()) {
            XsyncSubjectHistory subjectHistory = new XsyncSubjectHistory();
            subjectHistory.setLocalLabel(sub.getLocalLabel());
            subjectHistory.setSyncStatus(sub.getSyncStatus());
            subjectHistory.setSyncMessage(sub.getMessage());

            subjectHistory.setCreated(new Date());
            subjectHistory.setDisabled(new Date());
            subjectHistory.setTimestamp(new Date());
            subjectHistory.setEnabled(true);

            histories.add(subjectHistory);
        }
        syncHistory.setSubjectHistories(histories);
    }

    private void setExperiemntHistory() {
        List <XsyncExperimentHistory> histories = new ArrayList<>();

        for (SubjectSyncItem sub : manifest.getSubjects()) {
            for (ExperimentSyncItem exp : sub.getExperiments()) {
                XsyncExperimentHistory experimentHistory = new XsyncExperimentHistory();
                experimentHistory.setSubjectLabel(sub.getLocalLabel());
                experimentHistory.setLocalLabel(exp.getLocalLabel());
                experimentHistory.setSyncStatus(exp.getSyncStatus());
                experimentHistory.setSyncMessage(exp.getMessage());

                experimentHistory.setCreated(new Date());
                experimentHistory.setDisabled(new Date());
                experimentHistory.setTimestamp(new Date());
                experimentHistory.setEnabled(true);

                histories.add(experimentHistory);
            }
        }
        syncHistory.setExperimentHistories(histories);
    }

    private void setAssessorHistory() {
        List <XsyncAssessorHistory> histories = new ArrayList<>();

        for (SubjectSyncItem sub : manifest.getSubjects()) {
            // TODO sub.getAssessors()
            // Where are subject assessors?
            for (ExperimentSyncItem exp : sub.getExperiments()) {
                for (ExperimentSyncItem ass : exp.getAssessors()) {
                    XsyncAssessorHistory assessorHistory = new XsyncAssessorHistory();
                    assessorHistory.setLocalLabel(ass.getLocalLabel());
                    assessorHistory.setSyncStatus(ass.getSyncStatus());
                    assessorHistory.setSyncMessage(ass.getMessage());

                    assessorHistory.setCreated(new Date());
                    assessorHistory.setDisabled(new Date());
                    assessorHistory.setTimestamp(new Date());
                    assessorHistory.setEnabled(true);
                    histories.add(assessorHistory);
                }
            }
        }
        syncHistory.setAssessorHistories(histories);
    }

    private void setResourceHistory() {
        setResourceHistoryProps(manifest.getResources());

    /*    for (SubjectSyncItem sub : manifest.getSubjects()) {
            setResourceHistoryProps(sub.getResources());
            for (ExperimentSyncItem exp : sub.getExperiments()) {
                setResourceHistoryProps(exp.getResources());
                for (ScanSyncItem scan : exp.getScans()) {
                	setResourceHistoryProps(scan.getResources());
                }                
                for (ExperimentSyncItem ass : exp.getAssessors()) {
                	setResourceHistoryProps(ass.getResources());
                }
            }
        }
        */
    }

    private void setResourceHistoryProps(List<ResourceSyncItem> resources) {
        List <XsyncResourceHistory> histories = new ArrayList<>();

        for (ResourceSyncItem res : resources) {
            XsyncResourceHistory resourceHistory = new XsyncResourceHistory();
            resourceHistory.setSyncStatus(res.getSyncStatus());
            resourceHistory.setSyncMessage(res.getMessage());
            resourceHistory.setLocalLabel(res.getLocalLabel());
            resourceHistory.setFileCount(res.getFileCount());
            resourceHistory.setFileSize((Long) res.getFileSize());

            resourceHistory.setCreated(new Date());
            resourceHistory.setTimestamp(new Date());
            histories.add(resourceHistory);
        }
        syncHistory.setResourceHistories(histories);
    }

    private int countAllExperiments() {
        int experimentCount = 0;
        for (SubjectSyncItem sub : manifest.getSubjects()) {
            experimentCount += sub.getExperiments().size();
        }
        return experimentCount;
    }

    private int countAllAssessors() {
        int assessorCount = 0;
        for (SubjectSyncItem sub : manifest.getSubjects()) {
            // TODO sub.getAssessors()
            // Where are subject assessors?
            for (ExperimentSyncItem exp : sub.getExperiments()) {
                assessorCount += exp.getAssessors().size();
            }
        }
        return assessorCount;
    }

    private int countAllResources() {
        int resourceCount = 0;
        resourceCount += manifest.getResources().size();

        for (SubjectSyncItem sub : manifest.getSubjects()) {
            resourceCount += sub.getResources().size();

            for (ExperimentSyncItem exp : sub.getExperiments()) {
                resourceCount += exp.getResources().size();
                for (ScanSyncItem s:exp.getScans()) {
                	resourceCount += s.getResources().size();
                }
                for (ExperimentSyncItem ass:exp.getAssessors()) {
                	resourceCount += ass.getResources().size();
                }
            }
        }
        return resourceCount;
    }

    private String calculateTotalData() {
        Long totalBytes = 0L;
        //Project Resources
        for (ResourceSyncItem r:manifest.getResources()) {
        	 totalBytes += (Long)r.getFileSize();
        }
        for (SubjectSyncItem sub : manifest.getSubjects()) {
        	//Subject Resources
            for (ResourceSyncItem r:sub.getResources()) {
           	 totalBytes += (Long)r.getFileSize();
           }
        	for (ExperimentSyncItem exp : sub.getExperiments()) {
                totalBytes += exp.getTotalSyncedFileSize();
            }
        }
        return XsyncFileUtils.getFormattedFileSize(totalBytes);
        //return FileUtils.byteCountToDisplaySize(totalBytes);
    }
}
