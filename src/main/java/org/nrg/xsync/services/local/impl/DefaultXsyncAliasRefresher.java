package org.nrg.xsync.services.local.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.services.local.XsyncAliasRefreshService;
import org.nrg.xsync.services.remote.RemoteRESTService;
import org.nrg.xsync.utils.QueryResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;



/**
 * The Class DefaultXsyncAliasRefresher.
 *
 * @author Mohana Ramaratnam, Mike Hodge
 */
@Service
public class DefaultXsyncAliasRefresher implements XsyncAliasRefreshService{
	
	/** The logger. */
	private static final Logger logger = LoggerFactory.getLogger(DefaultXsyncAliasRefresher.class);
	
	private final RemoteAliasService _aliasService;
	private final RemoteRESTService _restService;
	private final JdbcTemplate _jdbcTemplate;
	private final SerializerService _serializer;
	private final QueryResultUtil _queryResultUtil;

	@Autowired
	public DefaultXsyncAliasRefresher(final RemoteAliasService aliasService, final RemoteRESTService restService, final JdbcTemplate jdbcTemplate, final SerializerService serializer, final QueryResultUtil queryResultUtil) {
		_aliasService = aliasService;
		_restService = restService;
		_jdbcTemplate = jdbcTemplate;
		_serializer = serializer;
		_queryResultUtil = queryResultUtil;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void refreshToken() {
		//Get all the connection information from the RemoteConnectionManager
		//For each of the connections
		//Acquire the lock before you refresh them
		final RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
		final List<RemoteAliasEntity> remoteAliasEntities = _aliasService.getAll();
		if (remoteAliasEntities == null || remoteAliasEntities.size()<1) {
			return;
		}
		for (final RemoteAliasEntity connEntity:remoteAliasEntities) {
			final RemoteConnection conn = remoteConnectionHandler.toRemoteConnection(connEntity);
			if (!isProjectSyncEnabled(conn.getLocalProject())) {
				continue;
			}
			logger.info("Refreshing Alias for " + conn.getUrl());
			DefaultXsyncAliasRefresherForAProject myRunnable = new DefaultXsyncAliasRefresherForAProject(connEntity, conn,_restService,_serializer);
		    Thread t = new Thread(myRunnable);
		    t.start();
		}
	}
	
	
	private boolean isProjectSyncEnabled(String sourceProjectId) {
		boolean isEnabled = false;
		List<Map<String,Object>> results = _queryResultUtil.getProjectSyncDetails(sourceProjectId);
		if (results!=null && results.size()>0) {
			Map<String,Object> syncProjectData = results.get(0);
			try {
				final Boolean enabled =(Boolean)syncProjectData.get("sync_enabled");
				isEnabled = enabled.booleanValue();
			}catch(Exception e) {
				
			}
		}
		return isEnabled;
	}
}
