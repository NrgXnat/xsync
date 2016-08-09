package org.nrg.xsync.manager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xsync.manifest.ResourceSyncItem;
import org.nrg.xsync.manifest.SubjectSyncItem;
import org.nrg.xsync.manifest.SyncManifest;
import org.nrg.xsync.manifest.SyncedItem;
import org.nrg.xsync.services.local.SyncManifestService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public class SynchronizationManager {
    
	private static final Logger _log = LoggerFactory.getLogger(SynchronizationManager.class);
	
	private static Map<String, Date> projectSyncStartTime = new HashMap<String,Date>();

	private static Map<String, SyncManifest> syncManifests = new HashMap<String,SyncManifest>();
	
	public static void BEGIN_SYNC(final SyncManifestService syncManifestService, final XsyncXnatInfo xnatInfo, String projectId, String remoteProjectId, String host, UserI user, final MailService mailService) {
		Date now = new Date();
		projectSyncStartTime.put(projectId, now);
		SyncManifest projectSyncManifest = new SyncManifest(syncManifestService, xnatInfo, mailService, projectId, remoteProjectId, host);
		projectSyncManifest.setSync_user(user);
		projectSyncManifest.setSync_start_time(now);
		syncManifests.put(projectId, projectSyncManifest);
	}



	public synchronized static void UPDATE_MANIFEST(String projectId, SyncedItem item) {
		  SyncManifest manifest = syncManifests.get(projectId);
		  if (manifest != null) {
			  if (manifest.getLocalProjectId().equals(projectId)) {
					if (item instanceof ResourceSyncItem) {
						manifest.addResource((ResourceSyncItem)item);
					}else if (item instanceof SubjectSyncItem) {
						manifest.addSubject((SubjectSyncItem)item);
					}
				}
		  }
	}
	
	public static void END_ERROR_FAILURE_SYNC(String projectId) {
	    SyncManifest manifest = syncManifests.get(projectId);
	    if (manifest != null) {
			manifest.informUser();
			File syncInfoFilePath = new File(GET_SYNC_FILE_PATH(projectId)+projectId+"_sync.html");
			manifest.syncInfoToFile(syncInfoFilePath);
	    }		
	}
	
	public static void END_SYNC(final SerializerService serializer, String projectId, final NamedParameterJdbcTemplate jdbcTemplate) {
		Date now = new Date();
//		projectSyncEndTime.put(projectId,now);
	    SyncManifest manifest = syncManifests.get(projectId);
	    if (manifest != null) {
		  manifest.setSync_end_time(now);
			XsyncUtils xsyncUtils = new XsyncUtils(serializer, jdbcTemplate, manifest.getSync_user());
			XsyncXsyncprojectdata syncProjectConfiguration = xsyncUtils.getSyncDetailsForProject(projectId);
			syncProjectConfiguration.getSyncinfo().setSyncStartTime(projectSyncStartTime.get(projectId));
			if (manifest.wasSyncSuccessfull()) {
				syncProjectConfiguration.getSyncinfo().setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED);
				syncProjectConfiguration.getSyncinfo().setSyncEndTime(now);
			} else {
				syncProjectConfiguration.getSyncinfo().setSyncStatus(XsyncUtils.SYNC_STATUS_FAILED);
				// Don't update sync end time for failed syncs.  We want the last successful sync.  Only initialize null dates.
				if (syncProjectConfiguration.getSyncinfo().getSyncEndTime()==null) {
					syncProjectConfiguration.getSyncinfo().setSyncEndTime(new Date(0));
				}
			}
			try {
				//Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
				EventMetaI c = EventUtils.DEFAULT_EVENT(manifest.getSync_user(),"ADMIN_EVENT occurred");
				syncProjectConfiguration.save(manifest.getSync_user(), false, true,c);
			}catch(Exception e) {
				_log.debug("Unable to save synchronization  details for project: " + projectId + " Cause:" + e.getMessage());
				
			}
			manifest.informUser();
			File syncInfoFilePath = new File(GET_SYNC_FILE_PATH(projectId)+projectId+"_sync.html");
			manifest.syncInfoToFile(syncInfoFilePath);
			manifest.syncInfoToDatabase();
			//Clean up the cache path contents
			if (manifest.wasSyncSuccessfull()) cleanUp(projectId);
	    }
	}
	
	private static void cleanUp(String projectId) {
		File folder = new File(GET_SYNC_FILE_PATH(projectId));
		if (folder.exists())
			rmdir(folder);
	}
	
	private static void rmdir(File folder) {
		  // check if folder file is a real folder
	      if (folder.isDirectory()) {
	          File[] list = folder.listFiles();
	          if (list != null) {
	              for (int i = 0; i < list.length; i++) {
	                  File tmpF = list[i];
	                  if (tmpF.isDirectory()) {
	                      rmdir(tmpF);
	                  }
	                  if (!tmpF.getAbsolutePath().endsWith("_sync.html"))
	                	  tmpF.delete();
	              }
	          }
	      }
	}	

	private static String timeToPath(Date d) {
		SimpleDateFormat ft = 
			      new SimpleDateFormat ("yyyy.MM.dd'SYNC'hh.mm.ss");
		if (d==null)
			d = new Date();
		return ft.format(d);
	}
	
	public static String GET_SYNC_FILE_PATH(String projectId) {
		String syncPath = ArcSpecManager.GetFreshInstance().getGlobalCachePath();
		String timestampFolder = timeToPath(projectSyncStartTime.get(projectId));
		syncPath += "SYNCHRONIZATION" + File.separator + projectId + File.separator +  timestampFolder + File.separator ; 
		return syncPath;
		
	}


	
	public static String GET_SYNC_FILE_PATH(String projectId, XnatSubjectdata subject) {
		return GET_SYNC_FILE_PATH(projectId) +  "subjects" + File.separator ;
	}

	public static String GET_SYNC_FILE_PATH(String projectId, XnatExperimentdata exp) {
		//return GET_SYNC_FILE_PATH(projectId) +  exp.getLabel() + File.separator ;
		return GET_SYNC_FILE_PATH(projectId);
	}

	public static String GET_SYNC_FILE_PATH_TO_SESSION(String projectId, XnatExperimentdata exp) {
		return GET_SYNC_FILE_PATH(projectId) +  exp.getLabel() + File.separator ;

	}

	
	public static String GET_SYNC_XAR_PATH(String projectId, XnatExperimentdata exp) {
		return GET_SYNC_FILE_PATH(projectId) + "XAR" + File.separator +  exp.getLabel() + File.separator ;
	}

}
