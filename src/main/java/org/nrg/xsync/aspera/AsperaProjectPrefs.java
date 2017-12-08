package org.nrg.xsync.aspera;

import java.io.File;

import org.nrg.framework.constants.Scope;
import org.nrg.prefs.annotations.NrgPreference;
import org.nrg.prefs.annotations.NrgPreferenceBean;
import org.nrg.prefs.beans.AbstractPreferenceBean;
import org.nrg.prefs.exceptions.InvalidPreferenceName;
import org.nrg.prefs.services.NrgPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NrgPreferenceBean(toolId = "xsyncAsperaProject", toolName = "XSync Aspera Project Preferences")
public class AsperaProjectPrefs extends AbstractPreferenceBean {

    @Autowired
    protected AsperaProjectPrefs(final NrgPreferenceService preferenceService) {
        super(preferenceService);
    }

    @NrgPreference
    public Boolean getAsperaEnabled() {
        return null;
    }
    
    public Boolean getAsperaEnabled(final String entityId) {
        return this.getBooleanValue(SCOPE, entityId, "asperaEnabled");
    }

    public void setAsperaEnabled(final String entityId, final Boolean asperaEnabled) {
        try {
            this.setBooleanValue(SCOPE, entityId, asperaEnabled, "asperaEnabled");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getAsperaNodeUrl() {
        return null;
    }

    public String getAsperaNodeUrl(final String entityId) {
        return this.getValue(SCOPE, entityId, "asperaNodeUrl");
    }

    public void setAsperaNodeUrl(final String entityId, final String url) {
        try {
            this.set(SCOPE, entityId, url, "asperaNodeUrl");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getAsperaNodeUser() {
        return null;
    }

    public String getAsperaNodeUser(final String entityId) {
        return this.getValue(SCOPE, entityId, "asperaNodeUser");
    }

    public void setAsperaNodeUser(final String entityId, final String username) {
        try {
            this.set(SCOPE, entityId, username, "asperaNodeUser");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getPrivateKey() {
        return null;
    }

    public String getPrivateKey(final String entityId) {
        return this.getValue(SCOPE, entityId, "privateKey");
    }

    public void setPrivateKey(final String entityId, final String privateKey) {
        try {
            this.set(SCOPE, entityId, privateKey, "privateKey");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getDestinationDirectory() {
    	return null;
    }

    public String getDestinationDirectory(final String entityId) {
    	final String destDir = this.getValue(SCOPE, entityId, "destinationDirectory");
    	return (destDir.endsWith(File.separator)) ? destDir : destDir + File.separator;
    }

    public void setDestinationDirectory(final String entityId, final String directory) {
        try {
        	final String dirValue = (directory.endsWith(File.separator)) ? directory : directory + File.separator;
            this.set(SCOPE, entityId, dirValue, "destinationDirectory");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getLogDirectory() {
        return null;
    }

    public String getLogDirectory(final String entityId) {
        return this.getValue(SCOPE, entityId, "logDirectory");
    }

    public void setLogDirectory(final String entityId, final String directory) {
        try {
            this.set(SCOPE, entityId, directory, "logDirectory");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getSshPort() {
        return null;
    }

    public String getSshPort(final String entityId) {
        return this.getValue(SCOPE, entityId, "sshPort");
    }

    public void setSshPort(final String entityId, final String port) {
        try {
            this.set(SCOPE, entityId, port, "sshPort");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference(defaultValue = "33001")
    public String getUdpPort() {
        return null;
    }

    public String getUdpPort(final String entityId) {
        return this.getValue(SCOPE, entityId, "udpPort");
    }

    public void setUdpPort(final String entityId, final String port) {
        try {
            this.set(SCOPE, entityId, port, "udpPort");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    private static final Logger _logger = LoggerFactory.getLogger(AsperaProjectPrefs.class);
    public static final Scope SCOPE = Scope.Project;
}
