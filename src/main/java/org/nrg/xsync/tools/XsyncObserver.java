package org.nrg.xsync.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;

import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xft.utils.FileUtils;
import org.nrg.xsync.manager.SynchronizationManager;
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
			//bWriter= new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile,true)));
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
	
	
	public void close(XnatAbstractresourceI syncResource) {
		try {
			Date now = new Date();
	        bWriter.write("-------------------" + now + "------------------------");
	        bWriter.newLine();
			bWriter.close();
			fw.close();
		}catch(Exception e) {
			logger.error("Unable to close file buffers");
		}
			if (syncResource != null) {
				XnatResourcecatalog catalog = (XnatResourcecatalog)syncResource; 
				File catalogLocation = new File(catalog.getUri()) ;
				File catalogParent = catalogLocation.getParentFile();
				File dest = new File(catalogParent.getAbsolutePath() + File.separator + logFile.getName());
				try {			
					FileUtils.CopyFile(logFile, dest, true);
				}catch(Exception e) {
					logger.error("Unable to save file " + logFile.getAbsolutePath() + " to " + dest.getAbsolutePath(),e);
				}
			}
		
	}
}
