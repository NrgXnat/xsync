package org.nrg.xsync.tools;

import java.io.File;

import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatImagesessiondataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.model.XnatSubjectdataI;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.connection.RemoteConnectionResponse;

/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncURIUtils {
	
	public void prepareResourceURIForXar(XnatImagesessiondata exp,XnatImageassessordata assess){
		for (final XnatAbstractresourceI res : assess.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, assess);
		}
	
		for (final XnatAbstractresourceI res : assess.getIn_file()) {
			modifyExptResource((XnatAbstractresource) res, assess);
		}
	
		for (final XnatAbstractresourceI res : assess.getOut_file()) {
			modifyExptResource((XnatAbstractresource) res, assess);
		}
	
	}

	
	public void prepareResourceURI(XnatExperimentdata exp){
		for (final XnatAbstractresourceI res : exp.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, exp);
		}
	}

	private void modifyExptResource(XnatAbstractresourceI resource, XnatExperimentdata orig)  {
		if (resource instanceof XnatResource) {
			if (((XnatResource) resource).getUri() != null) {
				String path = ((XnatResource) resource).getUri();
				int exp_label_index = path.indexOf("/"+ orig.getLabel()+"/");
				if (exp_label_index != -1) {
					String newURI = path.substring(exp_label_index+orig.getLabel().length()+2);
					if (newURI.startsWith(File.separator) || newURI.startsWith("/")) {
						newURI=newURI.substring(1);
					}
					((XnatResource) resource).setUri(newURI);
				}
			}
			//Clear the catalog entries metadata
		} else if (resource instanceof XnatResourceseries) {
			if (((XnatResourceseries) resource).getPath() != null) {
				String path = ((XnatResourceseries) resource).getPath();
				int exp_label_index = path.indexOf(orig.getLabel());
				String newURI = path.substring(exp_label_index+orig.getLabel().length());
				if (newURI.startsWith(File.separator)) {
					newURI=newURI.substring(1);
				}
				((XnatResource) resource).setUri(newURI);
			}
		}
		//Negative values indicate that the resource is being skipped
		if (resource.getFileCount() != null ) {
			resource.setFileCount(resource.getFileCount()>0?resource.getFileCount():-1*resource.getFileCount());
		}if (resource.getFileSize() != null) {
			resource.setFileSize((Long)resource.getFileSize()>0?resource.getFileSize():-1*(Long)resource.getFileSize());
		}
	}

	public void prepareResourceURIForXar(XnatImagesessiondata exp){
		for (final XnatAbstractresourceI res : exp.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, exp);
		}
		for (final XnatImagescandataI scan : ((XnatImagesessiondata) exp).getScans_scan()) {
			scan.setImageSessionId(exp.getLabel());
			for (final XnatAbstractresourceI res : scan.getFile()) {
				modifyExptResource((XnatAbstractresource) res,exp);
			}
		}
		for (final XnatReconstructedimagedataI recon : exp.getReconstructions_reconstructedimage()) {
			for (final XnatAbstractresourceI res : recon.getIn_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}
			for (final XnatAbstractresourceI res : recon.getOut_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}
		}
		for (final XnatImageassessordataI assess : exp.getAssessors_assessor()) {
			for (final XnatAbstractresourceI res : assess.getResources_resource()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}
	
			for (final XnatAbstractresourceI res : assess.getIn_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}
	
			for (final XnatAbstractresourceI res : assess.getOut_file()) {
				modifyExptResource((XnatAbstractresource) res, exp);
			}
	
		}
	}
	
	
	public void prepareResourceURIForXar(XnatSubjectassessordata exp){
		for (final XnatAbstractresourceI res : exp.getResources_resource()) {
			modifyExptResource((XnatAbstractresource) res, exp);
		}
	}

	public String getRemoteAssignedId(final RemoteConnectionResponse connectionResponse) {
		 String remote_id_with_line_breaks = connectionResponse.getResponseBody();
		 remote_id_with_line_breaks = remote_id_with_line_breaks.replaceAll("\\r\\n|\\r|\\n", "");
		 String remote_id_parts[] = remote_id_with_line_breaks.split("/");
		 String remote_id = remote_id_parts[remote_id_parts.length-1];
		 return remote_id;
	}

}
