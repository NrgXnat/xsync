package org.nrg.xsync.services.local;

import java.util.Date;
import java.util.List;

import org.nrg.framework.orm.hibernate.BaseHibernateService;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manifest.SyncManifest;
import org.nrg.xsync.manifest.history.XsyncProjectHistory;

public interface SyncManifestService extends BaseHibernateService<XsyncProjectHistory> {

    XsyncProjectHistory findByStartDate(final Date date);

    void persistHistory(final SyncManifest manifest);

    XsyncProjectHistory getRecentProjectSync(String localProjectId, String remoteProjectId);

    XsyncProjectHistory findMostRecentBySubject(final String projectId, final String subjectLabel);

    String getStacktraceForFailedSync(String inputUrl, String projectId) throws NotFoundException;
}