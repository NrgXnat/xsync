package org.nrg.xsync.transfer.client.cli.prefs;

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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@NrgPreferenceBean(toolId = "xsyncCliTransferSite", toolName = "XSync CLI Transfer Site Preferences")
public class CliTransferSitePrefs extends AbstractPreferenceBean {

    @Autowired
    protected CliTransferSitePrefs(final NrgPreferenceService preferenceService) {
        super(preferenceService);
    }

    @NrgPreference
    public String getCliTransferScript() {
        return this.getValue(CliTransferProjectPrefs.CLI_TRANSFER_SCRIPT);
    }

    public void setCliTransferScript(final String url) {
        try {
            this.set(url, CliTransferProjectPrefs.CLI_TRANSFER_SCRIPT);
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferHost() {
        return this.getValue(CliTransferProjectPrefs.CLI_TRANSFER_HOST);
    }

    public void setCliTransferHost(final String url) {
        try {
            this.set(url, CliTransferProjectPrefs.CLI_TRANSFER_HOST);
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferUser() {
        return this.getValue(CliTransferProjectPrefs.CLI_TRANSFER_USER);
    }

    public void setCliTransferUser(final String url) {
        try {
            this.set(url, CliTransferProjectPrefs.CLI_TRANSFER_USER);
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferRemoteDir() {
        return this.getValue(CliTransferProjectPrefs.CLI_TRANSFER_REMOTE_DIR);
    }

    public void setCliTransferRemoteDir(final String url) {
        try {
            this.set(url, CliTransferProjectPrefs.CLI_TRANSFER_REMOTE_DIR);
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    @NrgPreference
    public String getCliTransferPrivateKey() {
        return this.getValue(CliTransferProjectPrefs.CLI_TRANSFER_PRIVATE_KEY);
    }

    public void setCliTransferPrivateKey(final String url) {
        try {
            this.set(url, CliTransferProjectPrefs.CLI_TRANSFER_PRIVATE_KEY);
        } catch (InvalidPreferenceName invalidPreferenceName) {
            log.error("Invalid CLI Transfer preference name");
        }
    }

    public static final Scope SCOPE = Scope.Site;
}
