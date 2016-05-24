package org.nrg.xnat.restlet.extensions.xsync;

import org.apache.commons.lang.StringUtils;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.nrg.xsync.discoverer.ProjectInformation;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Mohana Ramaratnam
 *
 */
@XnatRestlet({ "/projects/{PROJECT_ID}/information" })
public class XsyncProjectInformationRestlet extends SecureResource {
    public static final String PARAM_PROJECT_ID = "PROJECT_ID";
    private final String _projectId;
    private final String _listChoices;
    private static final Logger _log = LoggerFactory.getLogger(XsyncProjectInformationRestlet.class);

	public XsyncProjectInformationRestlet(Context context, Request request, Response response) throws ResourceException {
		super(context, request, response);
        _projectId = (String) getRequest().getAttributes().get(PARAM_PROJECT_ID);
    	_listChoices = getQueryVariable("list");

        if (StringUtils.isBlank(_projectId)) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "No project specified");
        }
    	if (_listChoices == null) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "No entity specified for which project specific information is required");   		
    	}
       
        getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}
	
    @Override
    public Representation getRepresentation(Variant variant) {  
        if (_log.isDebugEnabled()) {
            _log.debug("Returning Project Information details");
        }
        ProjectInformation projectInformation = new ProjectInformation(_projectId);
        ObjectNode objectNode = projectInformation.getInformation(_listChoices);
        MediaType mt = overrideVariant(variant);
        return new StringRepresentation(objectNode.toString(), mt);
     }	
	
	
	
}
