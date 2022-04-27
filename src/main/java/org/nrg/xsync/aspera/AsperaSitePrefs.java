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
@NrgPreferenceBean(toolId = "xsyncAsperaSite", toolName = "XSync Aspera Site Preferences")
public class AsperaSitePrefs extends AbstractPreferenceBean {

    private static final long serialVersionUID = 3746363482831017436L;
	@Autowired
    protected AsperaSitePrefs(final NrgPreferenceService preferenceService) {
        super(preferenceService);
    }

    @NrgPreference
    public String getAsperaNodeUrl() {
        return this.getValue("asperaNodeUrl");
    }

    public void setAsperaNodeUrl(final String url) {
        try {
            this.set(url, "asperaNodeUrl");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getAsperaNodeUser() {
        return this.getValue("asperaNodeUser");
    }

    public void setAsperaNodeUser(final String username) {
        try {
            this.set(username, "asperaNodeUser");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getPrivateKey() {
        return this.getValue("privateKey");
    }

    public void setPrivateKey(final String privateKey) {
        try {
            this.set(privateKey, "privateKey");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getDestinationDirectory() {
    	final String destDir = this.getValue("destinationDirectory");
    	return (destDir == null || destDir.isEmpty() ||
    			destDir.endsWith(File.separator)) ? destDir : destDir + File.separator;
    }

    public void setDestinationDirectory(final String directory) {
        try {
        	final String dirValue = (directory.isEmpty() || directory.endsWith(File.separator)) ?
        			directory : directory + File.separator;
            this.set(dirValue, "destinationDirectory");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getLogDirectory() {
        return this.getValue("logDirectory");
    }

    public void setLogDirectory(final String directory) {
        try {
            this.set(directory, "logDirectory");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getSshPort() {
        return this.getValue("sshPort");
    }

    public void setSshPort(final String port) {
        try {
            this.set(port, "sshPort");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    @NrgPreference
    public String getUdpPort() {
        return this.getValue("udpPort");
    }

    public void setUdpPort(final String port) {
        try {
            this.set(port, "udpPort");
        } catch (InvalidPreferenceName invalidPreferenceName) {
            _logger.error("Invalid AsperaSend preference name");
        }
    }

    private static final Logger _logger = LoggerFactory.getLogger(AsperaSitePrefs.class);
    public static final Scope SCOPE = Scope.Site;
}
