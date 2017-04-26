package org.nrg.xsync.services.local.impl;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.nrg.xsync.configuration.XsyncSitePreferencesBean;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.services.local.XsyncAliasRefreshService;
import org.nrg.xsync.services.remote.RemoteRESTService;
import org.nrg.xsync.utils.QueryResultUtil;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.commons.lang.time.DateUtils;
import org.nrg.framework.services.SerializerService;
import org.springframework.beans.factory.annotation.Autowired;


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
	private final XsyncSitePreferencesBean _prefs;

	@Autowired
	public DefaultXsyncAliasRefresher(final RemoteAliasService aliasService, final RemoteRESTService restService, final JdbcTemplate jdbcTemplate, final SerializerService serializer, final QueryResultUtil queryResultUtil,final  XsyncSitePreferencesBean prefs) {
		_aliasService = aliasService;
		_restService = restService;
		_jdbcTemplate = jdbcTemplate;
		_serializer = serializer;
		_queryResultUtil = queryResultUtil;
		_prefs=prefs;
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
		long nextTokenRefreshTimeInMills = Calendar.getInstance().getTimeInMillis() + _prefs.getTokenRefreshIntervalInMillis();

		final List<RemoteAliasEntity> remoteAliasEntities = _aliasService.getAll();
		if (remoteAliasEntities == null || remoteAliasEntities.size()<1) {
			return;
		}
		for (final RemoteAliasEntity connEntity:remoteAliasEntities) {
			final RemoteConnection conn = remoteConnectionHandler.toRemoteConnection(connEntity);
			if (!isProjectSyncEnabled(conn.getLocalProject())) {
				continue;
			}
			if (!willExpireBeforeNextRefresh(connEntity,nextTokenRefreshTimeInMills)) {
				continue;
			}
			logger.info("Refreshing Alias for " + conn.getUrl());
			final DefaultXsyncAliasRefresherForAProject myRunnable = new DefaultXsyncAliasRefresherForAProject(connEntity, conn,_restService,_serializer,_aliasService);
		    Thread t = new Thread(myRunnable);
		    t.start();
		}
	}
	
	
	private boolean isProjectSyncEnabled(String sourceProjectId) {
		boolean isEnabled = false;
		final List<Map<String,Object>> results = _queryResultUtil.getProjectSyncDetails(sourceProjectId);
		if (results!=null && results.size()>0) {
			final Map<String,Object> syncProjectData = results.get(0);
			try {
				isEnabled = (((Integer)syncProjectData.get("sync_enabled")).intValue()==1);
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		return isEnabled;
	}
	
	/*
	 * If the token would expire before the next refresh time, refresh this token
	 */
	private boolean willExpireBeforeNextRefresh(RemoteAliasEntity connEntity, long nextTokenRefreshTimeInMills) {
		boolean willExpireBeforeNextRefresh = false;
		final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		Date tokenExpiresDate = connEntity.getEstimatedExpirationTime();
		Date nextTokenRefreshDate = new Date(nextTokenRefreshTimeInMills);
		//System.out.println("Expirtaion Date: " + formatter.format(tokenExpiresDate));
		//System.out.println("Token Refresh Date: " + formatter.format(nextTokenRefreshDate));
		//in milliseconds
		long diff = tokenExpiresDate.getTime() - nextTokenRefreshTimeInMills;
		//System.out.println("Diff in Millis: " + diff);
		if (diff < 0) { //Alias Token will expire before the next token refresh
			willExpireBeforeNextRefresh = true;
		}else { //How long do we have before the token expires
			int diffHours = ((int)(diff / (60 * 60 * 1000))); //Number of Hours between expiry and next token refresh
			if (diffHours < 12 ) { // Keep some buffer hours before the token expires and to allow the  token to be refreshed
				willExpireBeforeNextRefresh = true;
			}
		}
		//System.out.println("Next Refresh Date: " + formatter.format(nextTokenRefreshDate));
		//System.out.println("Will Refresh: " + willExpireBeforeNextRefresh);
		return willExpireBeforeNextRefresh;
	}
}
