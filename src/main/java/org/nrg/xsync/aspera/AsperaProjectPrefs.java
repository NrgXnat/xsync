package org.nrg.xsync.aspera;

import java.io.File;

import org.nrg.framework.constants.Scope;
import org.nrg.prefs.annotations.NrgPreference;
import org.nrg.prefs.annotations.NrgPreferenceBean;
import org.nrg.prefs.beans.AbstractPreferenceBean;
import org.nrg.prefs.entities.Preference;
import org.nrg.prefs.exceptions.InvalidPreferenceName;
import org.nrg.prefs.services.NrgPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NrgPreferenceBean(toolId = "xsyncAsperaProject", toolName = "XSync Aspera Project Preferences")
public class AsperaProjectPrefs extends AbstractPreferenceBean {
	
    public static final Scope SCOPE = Scope.Project;
	public static final String ASPERA_ENABLED = "asperaEnabled";
	public static final String ASPERA_NODE_URL = "asperaNodeUrl";
	public static final String ASPERA_NODE_USER = "asperaNodeUser";
	public static final String PRIVATE_KEY = "privateKey";
	public static final String DESTINATION_DIRECTORY = "destinationDirectory";
	public static final String LOG_DIRECTORY = "logDirectory";
	public static final String SSH_PORT = "sshPort";
	public static final String UDP_PORT = "udpPort";

    @Autowired
    protected AsperaProjectPrefs(final NrgPreferenceService preferenceService) {
        super(preferenceService);
    }

    @NrgPreference
    public Boolean getAsperaEnabled() {
        return null;
    }
    
    public Boolean getAsperaEnabled(final String entityId) {
        return this.getBooleanValue(SCOPE, entityId, ASPERA_ENABLED);
    }

    public void setAsperaEnabled(final String entityId, final Boolean asperaEnabled) {
        try {
        	removeSiteLevelPreferenceIfExists(ASPERA_ENABLED);
            this.setBooleanValue(SCOPE, entityId, asperaEnabled, ASPERA_ENABLED);
			if (sitePreferenceExists(ASPERA_ENABLED) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getAsperaNodeUrl() {
        return null;
    }

    public String getAsperaNodeUrl(final String entityId) {
        return this.getValue(SCOPE, entityId, ASPERA_NODE_URL);
    }

    public void setAsperaNodeUrl(final String entityId, final String url) {
        try {
            removeSiteLevelPreferenceIfExists(ASPERA_NODE_URL);
            this.set(SCOPE, entityId, url, ASPERA_NODE_URL);
			if (sitePreferenceExists(ASPERA_NODE_URL) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getAsperaNodeUser() {
        return null;
    }

    public String getAsperaNodeUser(final String entityId) {
        return this.getValue(SCOPE, entityId, ASPERA_NODE_USER);
    }

    public void setAsperaNodeUser(final String entityId, final String username) {
        try {
            removeSiteLevelPreferenceIfExists(ASPERA_NODE_USER);
            this.set(SCOPE, entityId, username, ASPERA_NODE_USER);
			if (sitePreferenceExists(ASPERA_NODE_USER) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getPrivateKey() {
        return null;
    }

    public String getPrivateKey(final String entityId) {
        return this.getValue(SCOPE, entityId, PRIVATE_KEY);
    }

    public void setPrivateKey(final String entityId, final String privateKey) {
        try {
            removeSiteLevelPreferenceIfExists(PRIVATE_KEY);
            this.set(SCOPE, entityId, privateKey, PRIVATE_KEY);
			if (sitePreferenceExists(PRIVATE_KEY) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getDestinationDirectory() {
    	return null;
    }

    public String getDestinationDirectory(final String entityId) {
    	final String destDir = this.getValue(SCOPE, entityId, DESTINATION_DIRECTORY);
    	return (destDir == null || destDir.isEmpty() || destDir.endsWith(File.separator)) ?
    			destDir : destDir + File.separator;
    }

    public void setDestinationDirectory(final String entityId, final String directory) {
        try {
        	final String dirValue = (directory.isEmpty() || directory.endsWith(File.separator)) ?
        			directory : directory + File.separator;
            removeSiteLevelPreferenceIfExists(DESTINATION_DIRECTORY);
            this.set(SCOPE, entityId, dirValue, DESTINATION_DIRECTORY);
			if (sitePreferenceExists(DESTINATION_DIRECTORY) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getLogDirectory() {
        return null;
    }

    public String getLogDirectory(final String entityId) {
        return this.getValue(SCOPE, entityId, LOG_DIRECTORY);
    }

    public void setLogDirectory(final String entityId, final String directory) {
        try {
            removeSiteLevelPreferenceIfExists(LOG_DIRECTORY);
            this.set(SCOPE, entityId, directory, LOG_DIRECTORY);
			if (sitePreferenceExists(LOG_DIRECTORY) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getSshPort() {
        return null;
    }

    public String getSshPort(final String entityId) {
        return this.getValue(SCOPE, entityId, SSH_PORT);
    }

    public void setSshPort(final String entityId, final String port) {
        try {
            removeSiteLevelPreferenceIfExists(SSH_PORT);
            this.set(SCOPE, entityId, port, SSH_PORT);
			if (sitePreferenceExists(SSH_PORT) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference(defaultValue = "33001")
    public String getUdpPort() {
        return null;
    }

    public String getUdpPort(final String entityId) {
        return this.getValue(SCOPE, entityId, UDP_PORT);
    }

    public void setUdpPort(final String entityId, final String port) {
        try {
            removeSiteLevelPreferenceIfExists(UDP_PORT);
            this.set(SCOPE, entityId, port, UDP_PORT);
			if (sitePreferenceExists(UDP_PORT) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

	// TODO:  Workaround method (XNAT-5134)
	private void removeSiteLevelPreferenceIfExists(String key) {
		try {
			if (sitePreferenceExists(key) != null) {
				this.delete(Scope.Site,"",key);
			}
		} catch (InvalidPreferenceName e) {
			// Do nothing.
		}
	}
	// TODO:  Workaround method (XNAT-5134)
	private Preference sitePreferenceExists(String key) {
		Preference p = this.getPreference(Scope.Site, "", key);
		if (p != null) {
			_logger.error("ERROR:  Found site level preference where only project preferences should exist (KEY=" + key + ").");
			
		}
		return p;
	}
	
    private static final Logger _logger = LoggerFactory.getLogger(AsperaProjectPrefs.class);
}
