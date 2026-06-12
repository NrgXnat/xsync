package org.nrg.xsync.services.local;

import java.util.Date;
import java.util.List;

import org.nrg.framework.orm.hibernate.BaseHibernateService;
import org.nrg.xsync.manifest.SyncManifest;
import org.nrg.xsync.manifest.XsyncProjectHistory;
import org.nrg.xsync.pojo.WhitelistSitePojo;
import org.nrg.xsync.pojo.XsyncRemoteUrlDetailsPojo;

public interface SyncManifestService extends BaseHibernateService<XsyncProjectHistory> {

    XsyncProjectHistory findByStartDate(final Date date);

    List<XsyncProjectHistory> findBySyncStatus(final String status);

    List<XsyncProjectHistory> findBySubject(final String subjectLabel);

    void persistHistory(final SyncManifest manifest);

    XsyncProjectHistory getRecentProjectSync(String localProjectId, String remoteProjectId);

    XsyncProjectHistory findMostRecentBySubject(final String projectId, final String subjectLabel);

    List<XsyncRemoteUrlDetailsPojo> findRemoteUrlDetails(boolean whitelistEnabled, List<WhitelistSitePojo> whitelist);
}