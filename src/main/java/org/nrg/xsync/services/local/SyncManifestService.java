package org.nrg.xsync.services.local;

import org.nrg.framework.orm.hibernate.BaseHibernateService;
import org.nrg.xsync.manifest.SyncManifestHistory;

import java.util.Date;
import java.util.List;

/**
 * Created by Michael Hileman on 2016/07/07.
 */
public interface SyncManifestService extends BaseHibernateService<SyncManifestHistory> {
    SyncManifestHistory findByStartDate(final Date date);
    List<SyncManifestHistory> findBySyncStatus(final String status);
    List<SyncManifestHistory> findBySubject(final String subjectLabel);
}
