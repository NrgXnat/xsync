package org.nrg.xsync.services.local.impl;

import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xsync.manifest.SyncManifestHistory;
import org.nrg.xsync.manifest.SyncManifestRepository;
import org.nrg.xsync.services.local.SyncManifestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by michael on 2016/07/07.
 */
@Service
public class HibernateSyncManifestService
        extends AbstractHibernateEntityService<SyncManifestHistory, SyncManifestRepository>
        implements SyncManifestService {

    @Transactional
    @Override
    public SyncManifestHistory findByStartDate(final Date date) {
        return getDao().findByUniqueProperty("startDate", date);
    }

    @Transactional
    @Override
    public List<SyncManifestHistory> findBySyncStatus(final String status) {
        return new ArrayList<>();
    }

    @Transactional
    @Override
    public List<SyncManifestHistory> findBySubject(final String subjectLabel) {
        return new ArrayList<>();
    }
}
