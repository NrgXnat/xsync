package org.nrg.xsync.transfer.client.cli.prefs;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CliTransferProjectPrefsInfo {

	private Boolean cliTransferEnabled;
	private String cliTransferScript;
	private String cliTransferHost;
	private String cliTransferUser;
	private String cliTransferRemoteDir;
	private String cliTransferPrivateKey;
    
	public CliTransferProjectPrefsInfo(CliTransferProjectPrefs prefs, String entityId) {
		this.cliTransferEnabled = prefs.getCliTransferEnabled(entityId);
		this.cliTransferScript = prefs.getCliTransferScript(entityId);
		this.cliTransferHost = prefs.getCliTransferHost(entityId);
		this.cliTransferUser = prefs.getCliTransferUser(entityId);
		this.cliTransferRemoteDir = prefs.getCliTransferRemoteDir(entityId);
		this.cliTransferPrivateKey = prefs.getCliTransferPrivateKey(entityId);
    }

}
