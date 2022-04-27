package org.nrg.xsync.transfer;

import java.io.File;
import java.io.IOException;

public interface TransferClientI {
	
	public String[] getCommandArray(String projectID, File xarFile) throws IOException;

	public boolean upload(String projectID, File xarFile) throws IOException, InterruptedException;
	
	//public boolean upload(String projectId, String sessionLabel) throws IOException, InterruptedException;
	
    public boolean isRunning(String project, String session);

    public boolean isRunning(String project);

}
