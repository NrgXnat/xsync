package org.nrg.xsync.transfer.client.cli;

import org.nrg.xsync.transfer.TransferClientI;
import org.nrg.xsync.transfer.client.AbstractTransferClient;
import org.nrg.xsync.transfer.client.TransferClientStatus;
import org.nrg.xsync.transfer.client.cli.prefs.CliTransferProjectPrefs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class CliTransferClient extends AbstractTransferClient implements TransferClientI {

    @Autowired
    public CliTransferClient(CliTransferProjectPrefs prefs) {
        super();
        _prefs = prefs;
        // Create a status for each client instance
        status = new TransferClientStatus();
        TransferClientStatus.list.add(status);
    }

	@Override
	public String[] getCommandArray(String projectID, File xarFile) throws IOException {
        return new String[] {
                _prefs.getCliTransferScript(projectID), 
                "--host", _prefs.getCliTransferHost(projectID),
                "--user", _prefs.getCliTransferUser(projectID),
                "--key", _prefs.getCliTransferPrivateKey(projectID),
                "--rdir", _prefs.getCliTransferRemoteDir(projectID),
                xarFile.getCanonicalPath()
        };
	}

    private static CliTransferProjectPrefs _prefs;

}
