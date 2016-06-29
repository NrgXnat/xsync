package org.nrg.xnat.restlet.extensions.xsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
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
@XnatRestlet({ "/xsync/projects/{PROJECT_ID}" })
public class XSyncNowRestlet extends SecureResource{
    public static final String PARAM_PROJECT_ID = "PROJECT_ID";
    private final String _projectId;
    private static final Logger _log = LoggerFactory.getLogger(XSyncNowRestlet.class);
   // @Autowired
   // ProjectChangeDiscover projectChangeDiscover;
    
    public XSyncNowRestlet(Context context, Request request, Response response) throws ResourceException {
		super(context, request, response);
	    _projectId = (String) getRequest().getAttributes().get(PARAM_PROJECT_ID);
        if (StringUtils.isBlank(_projectId)) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "No project specified");
        }
		
	}

	@Override
	public boolean allowPost() {
		return true;
	}

	@Override
	public void handlePost() {
		ExecutorService es = Executors.newSingleThreadExecutor();
		try {
		   	ProjectChangeDiscoverer projectChange = new ProjectChangeDiscoverer(_projectId,user);  	
			es.submit(projectChange);
	    	_log.info("Project " + _projectId + " is being exported");
            this.returnString(_projectId + "Synchronization Started", Status.SUCCESS_OK);
		}catch(XsyncNotConfiguredException xexception) {
			logger.error("Synchronization not configured exception for project " + _projectId + xexception.getMessage());
			getResponse().setStatus(Status.CLIENT_ERROR_EXPECTATION_FAILED, xexception, "Appears that the project " + _projectId + " is not configured properly for synchronization");
		}catch(Exception exception) {
			logger.error("Exception: " + exception.getMessage());
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, exception, exception.getMessage());
		}finally {
			es.shutdown();
		}
	}
}
