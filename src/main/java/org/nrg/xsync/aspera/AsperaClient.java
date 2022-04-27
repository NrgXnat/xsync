package org.nrg.xsync.aspera;

import org.nrg.xsync.transfer.TransferClientI;
import org.nrg.xsync.transfer.client.AbstractTransferClient;
import org.nrg.xsync.transfer.client.TransferClientStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class AsperaClient extends AbstractTransferClient implements TransferClientI {

    private static AsperaProjectPrefs _prefs;

    @Autowired
    public AsperaClient(AsperaProjectPrefs prefs) {
        super();
        _prefs = prefs;
        // Create a status for each client instance
        status = new TransferClientStatus();
        TransferClientStatus.list.add(status);
    }

	@Override
	public String[] getCommandArray(String projectID, File xarFile) throws IOException {
        return new String[] {
                "/usr/local/bin/ascp", "-v", "-l", "10G",
                "-P", _prefs.getSshPort(projectID),
                "-i", _prefs.getPrivateKey(projectID),
                "-L", _prefs.getLogDirectory(projectID),
                xarFile.getCanonicalPath(),
                String.format("%s@%s:%s",
                        _prefs.getAsperaNodeUser(projectID),
                        _prefs.getAsperaNodeUrl(projectID),
                        _prefs.getDestinationDirectory(projectID)
                )
        };
	}

}
