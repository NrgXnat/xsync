package org.nrg.xsync.transfer.client.cli.prefs;

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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@NrgPreferenceBean(toolId = "xsyncCliTransferProject", toolName = "XSync CLI Transfer Project Preferences")
public class CliTransferProjectPrefs extends AbstractPreferenceBean {
	
    private static final long serialVersionUID = 8303868747539629233L;
	public static final Scope SCOPE = Scope.Project;
	public static final String CLI_TRANSFER_ENABLED = "cliTransferEnabled";
	public static final String CLI_TRANSFER_SCRIPT = "cliTransferScript";
	public static final String CLI_TRANSFER_HOST = "cliTransferHost";
	public static final String CLI_TRANSFER_USER = "cliTransferUser";
	public static final String CLI_TRANSFER_REMOTE_DIR = "cliTransferRemoteDir";
	public static final String CLI_TRANSFER_PRIVATE_KEY = "cliTransferPrivateKey";

    @Autowired
    protected CliTransferProjectPrefs(final NrgPreferenceService preferenceService) {
        super(preferenceService);
    }

    @NrgPreference
    public Boolean getCliTransferEnabled() {
        return null;
    }
    
    public Boolean getCliTransferEnabled(final String entityId) {
        return this.getBooleanValue(SCOPE, entityId, CLI_TRANSFER_ENABLED);
    }

    public void setCliTransferEnabled(final String entityId, final Boolean clientEnabled) {
        try {
        	removeSiteLevelPreferenceIfExists(CLI_TRANSFER_ENABLED);
            this.setBooleanValue(SCOPE, entityId, clientEnabled, CLI_TRANSFER_ENABLED);
			if (sitePreferenceExists(CLI_TRANSFER_ENABLED) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferScript() {
        return null;
    }

    public String getCliTransferScript(final String entityId) {
        return this.getValue(SCOPE, entityId, CLI_TRANSFER_SCRIPT);
    }

    public void setCliTransferScript(final String entityId, final String url) {
        try {
            removeSiteLevelPreferenceIfExists(CLI_TRANSFER_SCRIPT);
            this.set(SCOPE, entityId, url, CLI_TRANSFER_SCRIPT);
			if (sitePreferenceExists(CLI_TRANSFER_SCRIPT) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferHost() {
        return null;
    }

    public String getCliTransferHost(final String entityId) {
        return this.getValue(SCOPE, entityId, CLI_TRANSFER_HOST);
    }

    public void setCliTransferHost(final String entityId, final String url) {
        try {
            removeSiteLevelPreferenceIfExists(CLI_TRANSFER_HOST);
            this.set(SCOPE, entityId, url, CLI_TRANSFER_HOST);
			if (sitePreferenceExists(CLI_TRANSFER_HOST) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferUser() {
        return null;
    }

    public String getCliTransferUser(final String entityId) {
        return this.getValue(SCOPE, entityId, CLI_TRANSFER_USER);
    }

    public void setCliTransferUser(final String entityId, final String url) {
        try {
            removeSiteLevelPreferenceIfExists(CLI_TRANSFER_USER);
            this.set(SCOPE, entityId, url, CLI_TRANSFER_USER);
			if (sitePreferenceExists(CLI_TRANSFER_USER) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferRemoteDir() {
        return null;
    }

    public String getCliTransferRemoteDir(final String entityId) {
        return this.getValue(SCOPE, entityId, CLI_TRANSFER_REMOTE_DIR);
    }

    public void setCliTransferRemoteDir(final String entityId, final String url) {
        try {
            removeSiteLevelPreferenceIfExists(CLI_TRANSFER_REMOTE_DIR);
            this.set(SCOPE, entityId, url, CLI_TRANSFER_REMOTE_DIR);
			if (sitePreferenceExists(CLI_TRANSFER_REMOTE_DIR) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferPrivateKey() {
        return null;
    }

    public String getCliTransferPrivateKey(final String entityId) {
        return this.getValue(SCOPE, entityId, CLI_TRANSFER_PRIVATE_KEY);
    }

    public void setCliTransferPrivateKey(final String entityId, final String url) {
        try {
            removeSiteLevelPreferenceIfExists(CLI_TRANSFER_PRIVATE_KEY);
            this.set(SCOPE, entityId, url, CLI_TRANSFER_PRIVATE_KEY);
			if (sitePreferenceExists(CLI_TRANSFER_PRIVATE_KEY) != null) {
				throw new RuntimeException("EXCEPTION:  Preference may not have been created.  Please try again.");
			}
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
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
			log.error("ERROR:  Found site level preference where only project preferences should exist (KEY=" + key + ").");
			
		}
		return p;
	}
	
}
