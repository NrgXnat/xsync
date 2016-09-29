package org.nrg.xsync.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;

import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.manifest.SyncManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncObserver implements Observer {
	private final static Logger logger = LoggerFactory.getLogger(XsyncObserver.class);

	
	final String projectId;
	private File logFile;
	private BufferedWriter bWriter;
	private FileWriter fw;
	
	public XsyncObserver(String projectId) {
		this.projectId = projectId;
		setup();
	}
	
	private void setup() {
		logFile = new File(SynchronizationManager.GET_SYNC_LOG_FILE_PATH(projectId));
		logFile.getParentFile().mkdirs();
		try {
			fw = new FileWriter(logFile);
			bWriter = new BufferedWriter(fw);
		} catch (IOException e) {
			logger.error("Unable to write to file " + e.getMessage());
		}
		
	}
	
	public void update(Observable observable, Object  item) {
		synchronized(this) {
			try {
				Date now = new Date();
		        bWriter.write("-------------------" + now + "------------------------");
		        bWriter.newLine();
		        bWriter.write(item.toString());
		        bWriter.newLine();
		        bWriter.write("-------------------------------------------------------");
		        bWriter.newLine();
		        bWriter.flush();
			} catch (IOException e) {
				logger.error("An error occurred writing the log sync file " + logFile.getAbsolutePath(), e);
			}
		}
	}
	
	public void close() {
		try {
			Date now = new Date();
	        bWriter.write("-------------------" + now + "------------------------");
	        bWriter.newLine();
			bWriter.close();
			fw.close();
		}catch(Exception e) {
			logger.error("Unable to close file buffers");
		}
	}
}
