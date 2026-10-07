package org.nrg.xsync.globus.services.impl.hibernate;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntityService;
import org.nrg.xapi.exceptions.DataFormatException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xsync.globus.GlobusCredentials;
import org.nrg.xsync.globus.entities.GlobusNodeConfig;
import org.nrg.xsync.globus.repositories.GlobusNodeConfigRepository;
import org.nrg.xsync.globus.services.GlobusNodeConfigService;
import org.springframework.stereotype.Service;

/**
 * Hibernate-backed {@link GlobusNodeConfigService}. Enforces a single row: a
 * save updates the existing configuration if present, otherwise creates it.
 *
 * @author XSync
 */
@Slf4j
@Service
@Transactional
public class HibernateGlobusNodeConfigService
        extends AbstractHibernateEntityService<GlobusNodeConfig, GlobusNodeConfigRepository>
        implements GlobusNodeConfigService {

    @Override
    public Optional<GlobusNodeConfig> getConfig() {
        final List<GlobusNodeConfig> all = getDao().findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    @Override
    public GlobusNodeConfig save(final GlobusNodeConfig config) throws DataFormatException {
        if (StringUtils.isAnyBlank(config.getClientId(), config.getOutboxCollectionId(), config.getOutboxDirectory())) {
            throw new DataFormatException("A Globus node configuration requires a client id, outbox collection id, "
                    + "and outbox directory.");
        }
        final Optional<GlobusNodeConfig> existingOpt = getConfig();
        if (existingOpt.isEmpty()) {
            if (StringUtils.isBlank(config.getClientSecret())) {
                throw new DataFormatException("A client secret is required when first configuring Globus for this node.");
            }
            getDao().saveOrUpdate(config);
            return config;
        }
        final GlobusNodeConfig existing = existingOpt.get();
        existing.setClientId(config.getClientId());
        existing.setOutboxCollectionId(config.getOutboxCollectionId());
        existing.setOutboxDirectory(config.getOutboxDirectory());
        existing.setInboxCollectionId(config.getInboxCollectionId());
        // A blank secret on update leaves the stored secret unchanged.
        if (StringUtils.isNotBlank(config.getClientSecret())) {
            existing.setClientSecret(config.getClientSecret());
        }
        getDao().saveOrUpdate(existing);
        return existing;
    }

    @Override
    public GlobusCredentials getServiceCredentials() throws NotFoundException {
        final GlobusNodeConfig config = getConfig()
                .orElseThrow(() -> new NotFoundException("Globus is not configured for this node."));
        return new GlobusCredentials(config.getClientId(), config.getClientSecret());
    }
}
