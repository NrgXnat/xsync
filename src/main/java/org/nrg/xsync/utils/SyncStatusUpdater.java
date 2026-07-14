package org.nrg.xsync.utils;

import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mohana Ramaratnam
 *
 */
public class SyncStatusUpdater {
       private static final Logger _log = LoggerFactory.getLogger(SyncStatusUpdater.class);

	    private final UserI _user;
	    private final String _projectId;
	    private final ProjectSyncConfiguration   _projectSyncConfiguration;

		public SyncStatusUpdater(final String projectId,final ProjectSyncConfiguration projectSyncConfiguration,final UserI user) {
			this._projectSyncConfiguration = projectSyncConfiguration;
			this._user = user;
			this._projectId = projectId;
		}
	
	   public void saveSyncBlockStatus(Boolean status) {
	        try {
	            _projectSyncConfiguration.getProjectSyncConfigurationFromDB().setSyncBlocked(status);
	            //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
	            EventMetaI c = EventUtils.DEFAULT_EVENT(_user, "ADMIN_EVENT occurred");
	            _projectSyncConfiguration.getProjectSyncConfigurationFromDB().save(_user, false, true, c);
	        } catch (Exception e) {
                _log.debug("Unable to save synchronization  details for project: {} Cause:{}", _projectId, e.getMessage());

	        }
	    }

}
