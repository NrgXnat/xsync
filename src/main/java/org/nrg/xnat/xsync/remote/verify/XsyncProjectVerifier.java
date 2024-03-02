package org.nrg.xnat.xsync.remote.verify;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.nrg.framework.services.SerializerService;
import org.nrg.xnat.xsync.anonymize.AnonScanResult;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.utils.JSONUtils;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.ResourceUtils;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.client.HttpServerErrorException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncProjectVerifier  {
	//This class handles procuring project resources at the destination
	
	private static final Logger _log = LoggerFactory.getLogger(XsyncProjectVerifier.class);
	
	private ProjectSyncConfiguration projectSyncConfiguration;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final RemoteConnectionManager _manager;
	private final QueryResultUtil _queryResultUtil;
	private final SerializerService _serializer;

	
    public XsyncProjectVerifier(final RemoteConnectionManager manager, final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, ProjectSyncConfiguration projectSyncConfiguration, SerializerService serializer) {
    	this.projectSyncConfiguration = projectSyncConfiguration;
    	this._manager = manager;
    	this._queryResultUtil = queryResultUtil;
		_jdbcTemplate = jdbcTemplate;
		_serializer = serializer;
    }

		public Map<String,String> verify(String local_catalog_file_path,String remote_project_id, String remote_resource_label, String uri) throws Exception {
			return verify(local_catalog_file_path, remote_project_id, remote_resource_label, uri, null);
		}

		public Map<String,String> verify(String local_catalog_file_path,String remote_project_id, String remote_resource_label, String uri, AnonScanResult anonScanResults) throws Exception{
		int count = 0;
		int maxTries = XsyncUtils.GLOBAL_RETRY_COUNTS;
		while(true) {
		    try {
		         return verifyWithoutRetry( local_catalog_file_path, remote_project_id,  remote_resource_label,  uri, anonScanResults) ;
		    } catch (RuntimeException e) {
		    	try {
		    		e.printStackTrace();
		    		_log.debug("Exception " + e.getMessage());
			    	if (maxTries > 0) {
				    	_log.error("Verification: Sleeping for " + XsyncUtils.GLOBAL_SLEEP_IN_MILLIS + " milliseconds");
			    		Thread.sleep(XsyncUtils.GLOBAL_SLEEP_IN_MILLIS);
			    	}
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) {
		        	Map<String,String> filesNotFound  = new HashMap<String,String>();
					filesNotFound.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_FAILED_TO_CONNECT);
					return filesNotFound;
		        }
		    }
		}
    }

    public RemoteConnectionResponse get(String uri) throws Exception{
		int count = 0;
		int maxTries = XsyncUtils.GLOBAL_RETRY_COUNTS;
		while(true) {
		    try {
		         return getWithoutRetry(uri) ;
		    } catch (Exception e) {
		    	try {
		    		e.printStackTrace();
		    		_log.debug("Exception " + e.getMessage());
			    	if (maxTries > 0) {
				    	_log.error("Verification: Sleeping for " + XsyncUtils.GLOBAL_SLEEP_IN_MILLIS + " milliseconds");
			    		System.out.println("Sleeping ");
				    	Thread.sleep(XsyncUtils.GLOBAL_SLEEP_IN_MILLIS);
			    	}
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) {
		        	throw e;
		        }
		    }
		}
    }

    private RemoteConnectionResponse getWithoutRetry(String uri) throws Exception  {
    	try {
	    	RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			RemoteConnectionResponse response = _manager.getResult(connection, uri);
			return response;
    	}catch(Exception  e) {
    		System.out.println("getWithoutRetry Erorr " + ExceptionUtils.getStackTrace(e));
 			_log.debug(e.getMessage());
			throw e;
    	}
    }
    
    private Map<String,String> verifyWithoutRetry(String local_catalog_file_path,String remote_project_id, String remote_resource_label, String uri, AnonScanResult anonScanResults) throws Exception {
    	//Get all the resources on the remote side for this project
    	Map<String,String> filesNotFound = null;
 		try {
	    	RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			RemoteConnectionResponse response = _manager.getResult(connection, uri);
			ResourceUtils resourceUtils = new ResourceUtils(this.projectSyncConfiguration);
			if (response.wasSuccessful()) {
				JSONUtils jsonUtils = new JSONUtils(_serializer);
				JsonNode jsonNode = jsonUtils.toJSONNode(response.getResponseBody());
				//Read the local Catalog and check if the file exists on the remote. 
				filesNotFound =  resourceUtils.verify(local_catalog_file_path, jsonNode, anonScanResults);

				if (filesNotFound.size() > 0) {
					//TODO: If files are missing - check for anon rejections
					filesNotFound.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_MISSING_FILES);
				}else {
					filesNotFound.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_VERIFIED_AND_COMPLETE);
				}
			}
 		}catch(HttpServerErrorException hse) {
 			//This is an Internal Server Error on a FILE URI // MOST Probably file does not exist on file system
 			// BUT is referenced on the catalog
 			// If this error is thrown due to some other reason - we will treat it as Missing Files
 			filesNotFound = new HashMap<String,String>();
			filesNotFound.put(XsyncUtils.XSYNC_VERIFICATION_STATUS, XsyncUtils.XSYNC_VERIFICATION_STATUS_MISSING_FILES);
 		}catch(Exception e) {
 			_log.debug(e.getMessage());
 			throw e;
 		}
 		return filesNotFound;
    }
}
