package org.nrg.xsync.tools;

import org.nrg.config.entities.Configuration;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.constants.Scope;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Mohana Ramaratnam
 *
 */
@Component
public class XsyncXnatInfo {

	public String getSiteId() {
	 return _preferences.getSiteId();
	}
	
	public String getDicomAnonymization(String projectId) {
		String anonymizationFromConfig = _configService.getConfig("xsync", "presyncanonymization", Scope.Project, projectId).getContents();
		return anonymizationFromConfig;
	}
	
	@Autowired
    private SiteConfigPreferences _preferences;
	
	@Autowired
	protected ConfigService _configService;


}
