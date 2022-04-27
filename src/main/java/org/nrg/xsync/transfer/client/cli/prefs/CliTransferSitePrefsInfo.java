package org.nrg.xsync.transfer.client.cli.prefs;

import java.io.File;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CliTransferSitePrefsInfo {
	
	private String cliTransferScript;
	private String cliTransferHost;
	private String cliTransferUser;
	private String cliTransferRemoteDir;
	private String cliTransferPrivateKey;
	
    public CliTransferSitePrefsInfo(CliTransferSitePrefs sitePrefs) {
		this.cliTransferScript = sitePrefs.getCliTransferScript();
		this.cliTransferHost = sitePrefs.getCliTransferHost();
		this.cliTransferUser = sitePrefs.getCliTransferUser();
		this.cliTransferRemoteDir = sitePrefs.getCliTransferRemoteDir();
		this.cliTransferPrivateKey = sitePrefs.getCliTransferPrivateKey();
    }

}
