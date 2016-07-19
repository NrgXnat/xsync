package org.nrg.xsync.services.local;

import org.nrg.framework.orm.hibernate.BaseHibernateService;
import org.nrg.xsync.manifest.XsyncProjectHistory;

import java.util.Date;
import java.util.List;

/**
 * Created by Michael Hileman on 2016/07/07.
 */
public interface SyncManifestService extends BaseHibernateService<XsyncProjectHistory> {
    XsyncProjectHistory findByStartDate(final Date date);
    List<XsyncProjectHistory> findBySyncStatus(final String status);
    List<XsyncProjectHistory> findBySubject(final String subjectLabel);
}
