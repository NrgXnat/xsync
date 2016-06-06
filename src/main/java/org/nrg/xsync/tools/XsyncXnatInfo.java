package org.nrg.xsync.tools;

import org.nrg.xdat.XDAT;
import org.nrg.xdat.preferences.SiteConfigPreferences;
//import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncXnatInfo {
	
	public XsyncXnatInfo() {
		_preferences = XDAT.getContextService().getBean(SiteConfigPreferences.class);
		
	}
	
	public String getSiteId() {
	 return _preferences.getSiteId();
	}
	
	//@Autowired
    private SiteConfigPreferences _preferences;

}
