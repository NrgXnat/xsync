package org.nrg.xsync.services.local.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.nrg.xdat.security.helpers.Users;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.services.local.DailySyncService;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XSyncFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author Mohana Ramaratnam
 *
 */
@Service
public class DefaultDailySyncService implements DailySyncService{
	@Override
	public void syncDaily() {
		//Get all projects with their sync schedules marked daily
		QueryResultUtil queryTools = new QueryResultUtil();
		List<Map<String,Object>> queryResultsRows = queryTools.getProjectsTobeSyncedDaily();
		//TODO
		//The user who sets up the sync will 
		//All project access will be done by the admin user
		if (queryResultsRows != null && queryResultsRows.size() > 0) {
		for (Map<String,Object> row:queryResultsRows) {
			String projectId =(String)row.get("source_project_id");
			String userId = (String)row.get("sync_scheduled_by");
			ExecutorService es = Executors.newSingleThreadExecutor();
			try {
				UserI user = Users.getUser(userId);
				ProjectChangeDiscoverer projectChange = new ProjectChangeDiscoverer(projectId,user);  	
				es.submit(projectChange);
			}catch(Exception e) {
				logger.debug(e.getMessage());
				XSyncFailureHandler.handle(projectId, e, "Daily sync failed");
			}finally {
				es.shutdown();
			}
		}
		}
	}
	
	private final static Logger logger = LoggerFactory.getLogger(DefaultDailySyncService.class);

}
