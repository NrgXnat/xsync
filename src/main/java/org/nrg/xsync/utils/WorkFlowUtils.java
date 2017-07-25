package org.nrg.xsync.utils;

import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.ItemI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;


/**
 * @author Mohana Ramaratnam
 *
 */
public class WorkFlowUtils {
	
	private static final Logger _log = LoggerFactory.getLogger(WorkFlowUtils.class);
	
	private ProjectSyncConfiguration projectSyncConfiguration;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
	private final RemoteConnectionManager _manager;
	private final QueryResultUtil _queryResultUtil;
	

    public WorkFlowUtils(final RemoteConnectionManager manager, final QueryResultUtil queryResultUtil, final NamedParameterJdbcTemplate jdbcTemplate, ProjectSyncConfiguration projectSyncConfiguration) {
    	this.projectSyncConfiguration = projectSyncConfiguration;
    	this._manager = manager;
    	this._queryResultUtil = queryResultUtil;
		_jdbcTemplate = jdbcTemplate;

    }
	
	public void createWorkflowAtRemote(XnatImagesessiondata img, String remoteId,String remoteProject, String status) {
		Class c = BaseElement.GetGeneratedClass(WrkWorkflowdata.SCHEMA_ELEMENT_NAME);
		ItemI o = null;
		try {
            o = (ItemI) c.newInstance();
            WrkWorkflowdata workFlowData = new WrkWorkflowdata(o);
  	        workFlowData.setLaunchTime(java.util.Calendar.getInstance().getTime());
            workFlowData.setDataType(img.getXSIType());
            workFlowData.setId(remoteId);
            workFlowData.setPipelineName("Xsync");
            workFlowData.setStatus(status);
            workFlowData.setExternalid(remoteProject);
			RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			RemoteConnectionResponse response = _manager.createWorkflow(connection, workFlowData);
            
		}catch(Exception e) {
        	_log.debug("Could not instantiate the workflow for " + img.getLabel());
        }
	}

	public void createWorkflowAtRemote(XnatSubjectdata sub, String remoteId,String remoteProject, String status) {
		Class c = BaseElement.GetGeneratedClass(WrkWorkflowdata.SCHEMA_ELEMENT_NAME);
		ItemI o = null;
		try {
            o = (ItemI) c.newInstance();
            WrkWorkflowdata workFlowData = new WrkWorkflowdata(o);
  	        workFlowData.setLaunchTime(java.util.Calendar.getInstance().getTime());
            workFlowData.setDataType(sub.getXSIType());
            workFlowData.setId(remoteId);
            workFlowData.setPipelineName("Xsync");
            workFlowData.setStatus(status);
            workFlowData.setExternalid(remoteProject);
			RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate, _queryResultUtil);
			RemoteConnection connection = remoteConnectionHandler.getConnection(projectSyncConfiguration.getProject().getId(),projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			RemoteConnectionResponse response = _manager.createWorkflow(connection, workFlowData);
            
		}catch(Exception e) {
        	_log.debug("Could not instantiate the workflow for " + sub.getLabel());
        }
	}
	
	


}
