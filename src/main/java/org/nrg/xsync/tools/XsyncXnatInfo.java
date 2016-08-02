package org.nrg.xsync.tools;

import org.nrg.config.services.ConfigService;
import org.nrg.framework.constants.Scope;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Mohana Ramaratnam
 */
@Component
public class XsyncXnatInfo {
    @Autowired
    public XsyncXnatInfo(final SiteConfigPreferences preferences, final ConfigService configService) {
        _preferences = preferences;
        _configService = configService;
    }

    public String getSiteId() {
        return _preferences.getSiteId();
    }

    public String getDicomAnonymization(String projectId) {
        return _configService.getConfig("xsync", "presyncanonymization", Scope.Project, projectId).getContents();
    }

    private final SiteConfigPreferences _preferences;
    private final ConfigService         _configService;
}
