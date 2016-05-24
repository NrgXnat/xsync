package org.nrg.xnat.restlet.extensions.xsync;

import java.util.ArrayList;

import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XsyncXsyncinfodata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.db.DBAction;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.event.persist.PersistentWorkflowUtils.EventRequirementAbsent;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xft.utils.ValidationUtils.ValidationResults;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.nrg.xnat.restlet.util.RequestUtil;
import org.nrg.xnat.utils.WorkflowUtils;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mohana Ramaratnam
 *
 */
@XnatRestlet({ "/xsync/setup" })
public class XsyncSetupRestlet extends SecureResource {

	XsyncXsyncprojectdata existing = null;
	String projectId = null;
	JsonNode synchronizationJson = null;
	static final String PROJECT_ELEMENT_JSON_NAME = "project"; 
	
	public XsyncSetupRestlet(Context context, Request request, Response response) throws ResourceException {
		super(context, request, response);
	}

	@Override
	public boolean allowPost() {
		return true;
	}
	@Override
	public boolean allowDelete() {
		return true;
	}
	@Override
	public void handleDelete() {
        PersistentWorkflowI wrk;
        try {
        	projectId = this.getQueryVariable("project");
            if (projectId == null) {
                this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
                return;
            }
            XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
            if (project == null) {
                this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
                return;
            }
        	ArrayList<XsyncXsyncprojectdata> list = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME+"/project_id", projectId, user, true);
            if (list != null && list.size() > 0) {
            	existing = list.get(0);
            }else {
                this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project: " + projectId);
                return;
            }
            wrk = WorkflowUtils.buildOpenWorkflow(user,existing.getXSIType(), existing.getXsyncXsyncprojectdataId()+"" ,existing.getProjectId(), newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.getDeleteAction(existing.getXSIType())));
            EventMetaI c = wrk.buildEvent();

            try {
            	if (existing.canDelete(user)) {
                    //1.7 Code
            		//DBAction.DeleteItem(existing.getItem().getCurrentDBVersion(), user, c, false);
            		DBAction.DeleteItem(existing.getItem().getCurrentDBVersion(), user, c);
            		WorkflowUtils.complete(wrk, c);
            	}
            } catch (Exception e) {
                try {
                    WorkflowUtils.fail(wrk, c);
                } catch (Exception e1) {
                    logger.error("", e1);
                }
                logger.error("", e);
                this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, e.getMessage());
            }
        } catch (EventRequirementAbsent e1) {
            logger.error("", e1);
            this.getResponse().setStatus(Status.CLIENT_ERROR_FORBIDDEN, e1.getMessage());
        }

	}
	
	@Override
	public void handlePost() {
		//curl -H "Content-Type: application/json" -X POST -d '{  "project":"TEST1ID",  "sync_frequency":"daily",  "auto_sync":"false",  "identifiers":"use_local",  "remote_url":"http://localhost:8080/xnat",  "remote_project_id":"SyncProjectId"}' -u admin  "http://localhost:8080/xnat/data/xsync/setup?project=TEST1ID"
		
		try {
			if (!((RequestUtil.hasContent(this.getRequest().getEntity())
					&& RequestUtil.compareMediaType(this.getRequest().getEntity(), MediaType.APPLICATION_JSON)))) {
				throw new Exception("POST data must be valid json format");
			}
			String jsonbody = this.getRequest().getEntity().getText();
			logger.debug(jsonbody);
			//Store the JSON to the Synchronization table
			ObjectMapper objectMapper = new ObjectMapper();
			synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);
	        projectId = synchronizationJson.get(PROJECT_ELEMENT_JSON_NAME).asText();
            XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectId, user, false);
            if (project == null) {
                this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to identify project");
                return;
            }else {
            	projectId = project.getId();
            }	
			XFTItem item = XFTItem.NewItem(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME, user);
            XsyncXsyncprojectdata syncProject = new XsyncXsyncprojectdata(item);

            ArrayList<XsyncXsyncprojectdata> list = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField(XsyncXsyncprojectdata.SCHEMA_ELEMENT_NAME+"/project_id", projectId, user, true);
            if (list != null && list.size() > 0) {
            	existing = list.get(0);
            	syncProject.setItem(existing.getItem());
            }else {

            }
            populate(syncProject,synchronizationJson);

            final ValidationResults vr = syncProject.validate();
            if (vr != null && !vr.isValid()) {
                this.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, vr.toFullString());
                return;
            }
            EventMetaI c = EventUtils.DEFAULT_EVENT(user, "Added Synchronization");
            
            if (SaveItemHelper.authorizedSave(syncProject, user, false, true, c)) {
            	PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user, syncProject.getXSIType(),syncProject.getXsyncXsyncprojectdataId()+"",syncProject.getProjectId(), newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.getAddModifyAction(syncProject.getXSIType(), (existing == null))));
            	WorkflowUtils.complete(wrk, c);
            }
            this.returnString(projectId + "Synchronization setup", (existing == null) ? Status.SUCCESS_CREATED : Status.SUCCESS_OK);

		}catch (Exception  exception) {
			logger.error("Exception: " + exception.getMessage());
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, exception, exception.getMessage());
		}
	}
	
	private void populate(XsyncXsyncprojectdata syncProject,JsonNode synchronizationJson) throws Exception {
		//Store
		/*{
			  project:"",
			  sync_frequency:"",
			  need_flag_to_synchronize:"",
			  auto_sync:"",
			  apply_anonymization:"",
			  identifiers:"",
			  remote_url:""
			}*/
		syncProject.setProjectId(synchronizationJson.get(PROJECT_ELEMENT_JSON_NAME).asText());
        XFTItem item = XFTItem.NewItem(XsyncXsyncinfodata.SCHEMA_ELEMENT_NAME, user);
        XsyncXsyncinfodata syncinfo = new XsyncXsyncinfodata(item);
        syncinfo.setSyncFrequency(synchronizationJson.get("sync_frequency").asText());
		syncinfo.setAutoSync(new Boolean(synchronizationJson.get("auto_sync").asBoolean()));
		syncinfo.setIdentifiers(synchronizationJson.get("identifiers").asText());
		syncinfo.setRemoteUrl(synchronizationJson.get("remote_url").asText());
		syncinfo.setRemoteProjectId(synchronizationJson.get("remote_project_id").asText());
		syncProject.setSyncinfo(syncinfo.getItem());
	}
}