package org.nrg.xsync.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.base.BaseXnatExperimentdata.UnknownPrimaryProjectException;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXWriter;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.manager.SynchronizationManager;
import org.restlet.data.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.nrg.xnat.exceptions.InvalidArchiveStructure;
import org.nrg.xnat.restlet.representations.ZipRepresentation;
import org.nrg.xnat.turbine.utils.ArcSpecManager;


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

				ArrayList<File> files = new ArrayList<File>(FileUtils.listFiles(pathToFiles,null,true));
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
	
	

/*	public File buildxar(UserI user, XnatExperimentdata orig, String targetproject,XnatSubjectdata targetsubject, XnatExperimentdata target) throws Exception {
		File xarFile;
		try {
			File experimentPath = new File(orig.getArchiveRootPath() + "arc001/" + orig.getArchiveDirectoryName());

			ZipRepresentation rep=new ZipRepresentation(MediaType.APPLICATION_ZIP,(orig).getArchiveDirectoryName(),0);

			List<File> files = (List<File>) FileUtils.listFiles(experimentPath,null,true);

			String expCachePath = ArcSpecManager.GetFreshInstance().getGlobalCachePath() + targetproject + File.separator+ user.getID()+File.separator+orig.getId()+File.separator+(new Date()).getTime();
			new File(expCachePath).mkdirs();
			File outF = new File(expCachePath, "expt_" + (new Date()).getTime() + ".xml");
			outF.deleteOnExit();
			FileOutputStream fos = new FileOutputStream(outF);
			SAXWriter writer = new SAXWriter(fos, true);
			writer.setAllowSchemaLocation(true);
			writer.setLocation(expCachePath);
			writer.setRelativizePath(((XnatSubjectassessordata) orig).getArchiveDirectoryName() + "/");

			orig.setId("");
			orig.setProject(target.getProject());
			if (orig instanceof XnatSubjectassessordata) {
				((XnatSubjectassessordata)orig).setSubjectId(targetsubject.getLabel());
			}
			if (orig instanceof XnatImagesessiondata) {
				for (XnatImagescandataI scan : ((XnatImagesessiondata)orig).getScans_scan()) {
					scan.setImageSessionId("");
				}
			}
			writer.write(orig.getItem());
			
			rep.addEntry(((XnatSubjectassessordata)target).getLabel() + ".xml",outF);
			rep.addAll(files);
			
			rep.setDownloadName(orig.getId()+".xar");
			xarFile = new File(expCachePath, (new Date()).getTime()+".xar");
			xarFile.deleteOnExit();
			rep.write(new FileOutputStream(xarFile));
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("Unable to retrieve/save session XML."+e.getMessage());
		}
		return xarFile;
		

		// return

	}
*/
}
