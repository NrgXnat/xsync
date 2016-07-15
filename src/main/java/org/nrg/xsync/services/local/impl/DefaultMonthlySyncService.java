package org.nrg.xsync.services.local.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.nrg.xdat.security.helpers.Users;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.services.local.MonthlySyncService;
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
public class DefaultMonthlySyncService implements MonthlySyncService{

	public void syncMonthly() {
		logger.info("Monthly Sync Triggered - BEGIN " + new Date());
		QueryResultUtil queryTools = new QueryResultUtil();
		List<Map<String,Object>> queryResultsRows = queryTools.getProjectsTobeSyncedMonthly();
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
					XSyncFailureHandler.handle(projectId, e, "Monthly sync failed");
				}finally{
					es.shutdown();
				}
			}
		}
		logger.info("Monthly Sync Trigger - END " + new Date());
	}
	
	private final static Logger logger = LoggerFactory.getLogger(DefaultMonthlySyncService.class);

}
