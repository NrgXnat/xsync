package org.nrg.xsync.utils;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.exceptions.NotFoundException;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.CatEntryI;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xnat.utils.CatalogUtils;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.manifest.ResourceSyncItem;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Mohana Ramaratnam
 *
 */
@Slf4j
public class ResourceUtils {
	
	private final ProjectSyncConfiguration   _projectSyncConfiguration;
	
	public ResourceUtils(ProjectSyncConfiguration   projectSyncConfiguration) {
		_projectSyncConfiguration = projectSyncConfiguration;
	}
	
	  public XnatAbstractresource getResource(String resourceLabel) {
	        XnatProjectdata project = _projectSyncConfiguration.getProject();
	        XnatAbstractresource projectResource = null;
	        List<XnatAbstractresourceI> resources = project.getResources_resource();
	        for (XnatAbstractresourceI resource : resources) {
	            if (resource.getLabel() != null && resource.getLabel().equals(resourceLabel)) {
	                projectResource = (XnatAbstractresource) resource;
	                break;
	            }else if (resource.getLabel() == null && resourceLabel == null) { //NO LABEL case
	                projectResource = (XnatAbstractresource) resource;
	                break;
	            }
	        }
	        return projectResource;
	    }
	  
	  
	  public void setSyncStatus(Map<String,String> fileComparison, ResourceSyncItem resourceSyncItem, String msg) {
          if (fileComparison == null) return;
		  String verificationStatus = fileComparison.get(XsyncUtils.XSYNC_VERIFICATION_STATUS);
          if (verificationStatus != null && verificationStatus.equals(XsyncUtils.XSYNC_VERIFICATION_STATUS_VERIFIED_AND_COMPLETE)) {
              resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED);
              resourceSyncItem.setMessage(msg + " updated ");
          }else if (verificationStatus != null && verificationStatus.equals(XsyncUtils.XSYNC_VERIFICATION_STATUS_MISSING_FILES)) {
              resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_INCOMPLETE);
              String msgWithMissingFiles = msg + " sync incomplete. ";
              for (Map.Entry<String, String> entry:fileComparison.entrySet()) {
            	  if (entry.getKey().equals(XsyncUtils.XSYNC_VERIFICATION_STATUS)) {
            		  continue;
            	  }
            	  msgWithMissingFiles += entry.getKey();
              }
              resourceSyncItem.setMessage(msgWithMissingFiles);
          }else  {
              resourceSyncItem.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED);
              String msgWithMissingFiles = msg + " could not verify sync. ";
              for (Map.Entry<String, String> entry:fileComparison.entrySet()) {
            	  if (entry.getKey().equals(XsyncUtils.XSYNC_VERIFICATION_STATUS)) {
            		  continue;
            	  }
            	  msgWithMissingFiles += entry.getKey();
              }
              resourceSyncItem.setMessage(msgWithMissingFiles);
          }
	  }
	  
	  public Map<String, String> verify(String localCatalogFilePath, JsonNode remoteFiles) throws NotFoundException {
		  Map<String, String> filesNotFound = new HashMap<String, String>();
		  JsonNode resultNode = getResultNode(remoteFiles);
		  File catalogFile = new File(localCatalogFilePath);
		  CatCatalogBean catalogBean = CatalogUtils.getCatalog(catalogFile, null);
		  if (catalogBean == null) {
		  	throw new NotFoundException("Catalog not found: " + localCatalogFilePath);
		  }
		  List<CatEntryI> entries = catalogBean.getEntries_entry();
		  for (CatEntryI entry:entries) {
			  String entryName = entry.getName();
			  String entryUri = entry.getUri();
			  if (entryName == null) {
				  entryName = entryUri;
			  }
			  boolean fileExists = fileExists(entryName, entryUri, resultNode);
			  if (!fileExists) {
				  filesNotFound.put(entryName + " not found " , entryUri);
			  }
		  }
		  return filesNotFound;
	  }
	  
	  private boolean fileExists(String fileName, String fileURI, JsonNode resultNode) {
		  Iterator<JsonNode> iterator = resultNode.elements();
		  boolean found = false;
		  try {
		      while (iterator.hasNext()) {
		            JsonNode fileNode = iterator.next();
		            String nodeFileName = fileNode.get("Name").asText();
		            String nodeFileURI = fileNode.get("URI").asText();
		            if (fileName.equals(nodeFileName)) { //&& fileURI.equals(nodeFileURI)) {
		            	found = true;
		            	break;
		            }
		      }
		  }catch(Exception e) {}
		  return found;
	  }
	  
	  private JsonNode getResultNode(JsonNode rootNode) {
		  JsonNode resultSetNode = rootNode.get("ResultSet");
		  JsonNode resultNode = resultSetNode.get("Result");
		  return resultNode;
	  }

	@SuppressWarnings("unused")
	private String getResourcePath(XnatImageassessordata orig, XnatAbstractresource resource) {
		String path  = null;
		XnatAbstractresource origResource = null;
		for (XnatAbstractresourceI r:orig.getResources_resource()) {
			if (r.getLabel().equals(resource.getLabel())) {
				origResource = (XnatAbstractresource)r;
				break;
			}
		}
		if (origResource != null) {
			try {
				path = origResource.getFullPath(orig.getArchiveRootPath());
			}catch(Exception e) {
				log.error("Could not get resource {} path", resource.getLabel(), e);
			}
		}
		return path;
	}

	@SuppressWarnings("unused")
	private String getResourcePath(String parent, XnatAbstractresource resource) {
		String path  = null;
		try {
			path = resource.getFullPath(parent);
		}catch(Exception e) {
			log.error("Could not get resource {} path", resource.getLabel(), e);
		}
		return path;
	}



}
