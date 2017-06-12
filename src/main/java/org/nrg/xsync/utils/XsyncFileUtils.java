package org.nrg.xsync.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

//import org.apache.commons.io.FileUtils;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xft.ItemI;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.restlet.representations.ZipRepresentation;
import org.nrg.xsync.manager.SynchronizationManager;
import org.restlet.data.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Mohana Ramaratnam
 *
 */
public class XsyncFileUtils {
	private static final Logger _log = LoggerFactory.getLogger(XsyncFileUtils.class);
	public static final String SYNCHRONIZATION_LABEL = "synchronization";
	
	public File buildZip(String remoteProjectId,File pathToFiles) throws Exception {
		File zipFile = null;
		try {
			String expCachePath = SynchronizationManager.GET_SYNC_FILE_PATH(remoteProjectId);
			new File(expCachePath).mkdirs();
			String path = pathToFiles.getAbsolutePath();
			if (pathToFiles.exists()) {
				ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP,pathToFiles.getParent(),0);

				ArrayList<File> files = new ArrayList<File>(org.apache.commons.io.FileUtils.listFiles(pathToFiles,null,true));
				ArrayList<File> fileteredFiles = new ArrayList<File>();
				//Hack
				for (File f:files) {
					if (!f.getName().endsWith("_catalog.xml"))
						fileteredFiles.add(f);
				}
				if (fileteredFiles.size()> 0) {
					rep.addAllAtRelativeDirectory(path, fileteredFiles);
				}
				zipFile = new File(expCachePath, (new Date()).getTime()+".zip");
				zipFile.deleteOnExit();
				rep.write(new FileOutputStream(zipFile));
			}
		} catch (Exception e) {
			_log.debug(e.toString() + "  " + e.getMessage());
			//e.printStackTrace();
			throw new Exception("Unable to create/save zip file "+e.getMessage());
		}
		return zipFile;

	}

	
	public static  String GetSyncFilPath(XnatExperimentdata exp) {
		String path = null;
		path = exp.getCachePath() ;
		return path;
	}
	
	public static String getFormattedFileSize(Long size) {
		//Note: MR Nov 9, 2016 - UI will take for units, this will enable sorting on UI
		//if (size < 1024) {
        //    return size + " B";
        //}
        //int exp = (int) (Math.log(size) / Math.log(1024));
        //return String.format("%.1f %sB", size / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
		return size.toString();
	}

	public static String getHumanReadableFileSize(Long size) {
		if (size < 1024) {
            return size + " B";
        }
        int exp = (int) (Math.log(size) / Math.log(1024));
        return String.format("%.1f %sB", size / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
	}

	
	public static String getAnonymizedSessionPath(XnatExperimentdata orig) {
		return SynchronizationManager.GET_SYNC_FILE_PATH_TO_SESSION(orig.getProject(),orig) ;

	}

	public static XnatAbstractresourceI createSynchronizationLogResource(XnatProjectdata project, final UserI _user) throws Exception {
	    	boolean synchronizationResourceExists  = false;
	    	for (XnatAbstractresourceI r: project.getResources_resource()) {
	    		if (r.getLabel()!= null && r.getLabel().equalsIgnoreCase(XsyncUtils.PROJECT_SYNC_LOG_RESOURCE_LABEL)) {
	    			synchronizationResourceExists = true;
	    		}
	    		if (synchronizationResourceExists) {
	    			return r;
	    		}
	    	}
	    	if (!synchronizationResourceExists) {
	    		//Create the resource
	    		//Create a catalog
	    		Class c = BaseElement.GetGeneratedClass(XnatResourcecatalog.SCHEMA_ELEMENT_NAME);
	    		ItemI o = null;
	            o = (ItemI) c.newInstance();

	    		XnatResourcecatalog catResource = (XnatResourcecatalog)BaseElement.GetGeneratedItem(o);
	    		catResource.setLabel(XsyncUtils.PROJECT_SYNC_LOG_RESOURCE_LABEL);
	    		catResource.setContent(XsyncUtils.PROJECT_SYNC_LOG_RESOURCE_LABEL);
	    		
	    		String resourceFolder=catResource.getLabel();
	    		String dest_path = org.nrg.xft.utils.FileUtils.AppendRootPath(project.getArchiveRootPath() , "resources/" );
	    		File dest=null;
	    		CatCatalogBean cat = new CatCatalogBean();
	    		cat.setId(catResource.getLabel());

	    		if(resourceFolder==null){
	    			dest = new File(new File(dest_path),cat.getId() + "_catalog.xml");
	    		}else{
	    			dest = new File(new File(dest_path,resourceFolder),cat.getId() + "_catalog.xml");
	    		}
	    		dest.getParentFile().mkdirs();
	    		try {
	    			FileWriter fw = new FileWriter(dest);
	    			cat.toXML(fw, true);
	    			fw.close();
	    		} catch (IOException e) {
	    			_log.error("",e);
	    		}

	    		catResource.setUri(dest.getAbsolutePath());
	    		project.addResources_resource(catResource);
		       try {
		   	        EventMetaI e = EventUtils.DEFAULT_EVENT(_user, "ADMIN_EVENT occurred");
		            boolean saved = project.save(_user, false, false, e);
		            if (!saved) {
		            	_log.error("Unable to save " + project.getId() + ". User " + _user.getLogin() + " may not have sufficient privileges");
		            }
		        }catch(Exception e) {
		        	_log.error("Unable to save " + project.getId() + ". User " + _user.getLogin() + " may not have sufficient privileges");
		        }
	    	     		
	    		return catResource;
	    	}
	    	return null;
	    }
	

}
