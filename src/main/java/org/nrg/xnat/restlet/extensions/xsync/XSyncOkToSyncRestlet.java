package org.nrg.xnat.restlet.extensions.xsync;

import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XsyncXsyncassessordata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.nrg.xsync.utils.XsyncUtils;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mohana Ramaratnam
 *
 */
@XnatRestlet({ "/xsync/experiments/{EXPT_ID}" })
public class XSyncOkToSyncRestlet extends SecureResource{
    public static final String PARAM_EXP_ID = "EXPT_ID";
    private final String _exptId;
    private Boolean _okToSync;
    private static final Logger _log = LoggerFactory.getLogger(XSyncOkToSyncRestlet.class);

    public XSyncOkToSyncRestlet(Context context, Request request, Response response) throws ResourceException {
		super(context, request, response);
	    _exptId = (String) getRequest().getAttributes().get(PARAM_EXP_ID);
	    _okToSync = false;
        if (StringUtils.isBlank(_exptId)) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "No experiment specified");
        }
 	}

	@Override
	public boolean allowPost() {
		return true;
	}

	@Override
	public void handlePost() {
		String okToSync = this.getQueryVariable("okToSync"); 
		_okToSync = new Boolean(okToSync); 
        if (_okToSync == null) {
            this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Unable to set OK to Sync" );
            return;
        }
        //If the OkToSync Assessor already exists, update that
        //If it does not exist, create one.
        XsyncXsyncassessordata okToSyncData = null;

        XnatExperimentdata experiment = XnatExperimentdata.getXnatExperimentdatasById(_exptId, user, false);
		XsyncUtils xsyncUtils = new XsyncUtils(user);
		XsyncXsyncprojectdata syncProjectConfiguration = xsyncUtils.getSyncDetailsForProject(experiment.getProject());

	
        try {
	        ArrayList<XsyncXsyncassessordata> okToSyncDatas =  XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", _exptId, user, true);
	        if (okToSyncDatas!=null && okToSyncDatas.size()>0) {
	        	okToSyncData = okToSyncDatas.get(0);
	        	if (okToSyncData.getOktosync() != this._okToSync) {
	        		okToSyncData.setOktosync(_okToSync);
	        	}
	        }else {
	        	//Create a new one
	        	okToSyncData = new XsyncXsyncassessordata(); 
	        	okToSyncData.setId(XsyncXsyncassessordata.CreateNewID());
	        	okToSyncData.setLabel(experiment.getLabel() + "_XSYNC_INFO");
	        	okToSyncData.setProject(experiment.getProject());
	        	if (okToSyncData.getDate() == null ) okToSyncData.setDate(new Date()); //Setting first time
	        	okToSyncData.setAuthorizedBy(user.getLogin());
	        	okToSyncData.setAuthorizedTime(new Date());
	        	okToSyncData.setRemoteUrl(syncProjectConfiguration.getSyncinfo().getRemoteUrl());
	        	okToSyncData.setRemoteProjectId(syncProjectConfiguration.getSyncinfo().getRemoteProjectId());
	        	if (experiment instanceof XnatSubjectassessordata) {
		        	okToSyncData.setSubjectId(((XnatSubjectassessordata)experiment).getSubjectId());
	        	}else {
	        		throw new Exception("Expecting a subject assessor to set synchronization");
	        	}
	        	okToSyncData.setSyncedExperimentId(_exptId);
	        	okToSyncData.setOktosync(this._okToSync);
	        }
	        if (okToSyncData != null) {
    			//Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
    			EventMetaI c = EventUtils.DEFAULT_EVENT(user,"ADMIN_EVENT occurred");
    			boolean saved = okToSyncData.save(user, false, true,c);
    			if (!saved) {
    		        this.returnString("Unable to save the Ok To Sync Information", Status.SERVER_ERROR_INTERNAL);
    			}
	        }
	        this.returnString(_exptId + " has been marked "+ (_okToSync?"OK":"Not OK") +" to sync", Status.SUCCESS_OK);
        }catch(Exception e) {
	        this.returnString(e.getLocalizedMessage(), Status.SERVER_ERROR_INTERNAL);
        }
	}

}
