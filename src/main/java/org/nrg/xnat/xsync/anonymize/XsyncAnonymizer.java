package org.nrg.xnat.xsync.anonymize;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.nrg.action.ServerException;
import org.nrg.transaction.operations.CopyOp;
import org.nrg.transaction.OperationI;
import org.nrg.transaction.TransactionException;
import org.nrg.xdat.model.CatEntryI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatResourcecatalogI;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.base.BaseXnatExperimentdata.UnknownPrimaryProjectException;
import org.nrg.xnat.utils.CatalogUtils;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

// TODO: Auto-generated Javadoc
/**
 * SimpleExportAnonymizerServiceImpl to anonymize both the data and xml for a
 * session.
 * 
 * 
 * @author james@radiologics.com
 *
 */

@Service
public class XsyncAnonymizer implements AnonymizerI {

	/** The Constant DateDefault. */
	final static String DateDefault = "2000-01-01";
	
	/** The Constant PatientBirthDateDefault. */
	final static String PatientBirthDateDefault = null;

	/** The Constant PatientID. */
	final static String PatientID = "PatientID";
	
	/** The Constant StudyInstanceUID. */
	final static String StudyInstanceUID = "StudyInstanceUID";
	
	/** The Constant SeriesInstanceUID. */
	final static String SeriesInstanceUID = "SeriesInstanceUID";
	
	/** The Constant PatientName. */
	final static String PatientName = "PatientName";
	
	/** The Constant AccessionNumber. */
	final static String AccessionNumber = "AccessionNumber";
	
	/** The Constant PatientBirthDate. */
	final static String PatientBirthDate = "PatientBirthDate";
	
	/** The Constant ScanDate. */
	final static String ScanDate = "Date";

	private final XsyncXnatInfo _xsyncXnatInfo;

	@Autowired
	public XsyncAnonymizer(final XsyncXnatInfo xsyncXnatInfo) {
		_xsyncXnatInfo = xsyncXnatInfo;
	}

	/* (non-Javadoc)
	 * @see org.nrg.xnat.services.transfer.local.SimpleExportAnonymizerService#anonymize(org.nrg.xdat.om.XnatImagesessiondata, java.lang.String)
	 */
	// have to rename files.
	@Override
	public void anonymize(final XnatImagesessiondata session, final String destProject, final String cacheSessionPath) throws Exception {
		try {
			for(XnatImagescandataI scan: session.getScans_scan()){
				final ExportAnonymizer anonymizer = new ExportAnonymizer(_xsyncXnatInfo, session, destProject, cacheSessionPath, scan);
				boolean rejected = this.applyAnonymizationToFiles(session, cacheSessionPath, anonymizer);

				if (rejected) {
					for(int i = 0; i < session.getScans_scan().size(); i++) {
						if(StringUtils.equals(session.getScans_scan().get(i).getId(),scan.getId())){
							session.getScans_scan().remove(i);
							break;
						}
					}
				}
			}
		} catch (TransactionException e) {
			logger.error("applyAnonymizationToFiles", e);
			throw new Exception(e);
		}
		try {
			this.applyAnonymizationToXml(session, cacheSessionPath);
		} catch (Exception e) {
			throw new Exception("Failed to anonymize xml:" + session.getLabel());
		}
	}

	
/*	private void copyScanFiles(final XnatImagesessiondata session, File cachePath) throws IOException {
		for(final XnatImagescandataI scan: session.getScans_scan()) {
			for (final XnatAbstractresourceI res:scan.getFile()) {
				if (res instanceof XnatResource) {
					final XnatResource abs=(XnatResource)res;
					if (StringUtils.isNotEmpty(abs.getFormat()) && abs.getFormat().equals("DICOM")){
						File rscFile = new File(abs.getUri());
						File scanFolder = new File(rscFile.getParent());
						try {
							if (abs.getFileCount() >0  && ((Long)abs.getFileSize()).longValue() > 0 ) {
								FileUtils.copyDirectoryToDirectory(scanFolder, cachePath);
							}
						}catch(ClassCastException ce) {
							FileUtils.copyDirectoryToDirectory(scanFolder, cachePath);
						}
					}
				}
			}
		}
	}
*/	
	
	/**
	 * Apply anonymization script.
	 *
	 * @param session the session
	 * @param anonymizer the anonymizer
	 * @throws TransactionException the transaction exception
	 */
	public boolean applyAnonymizationToFiles(final XnatImagesessiondata session,String sessionPath, final ExportAnonymizer anonymizer) throws TransactionException{
		final boolean[] rejected = new boolean[1];
		if(session instanceof XnatImagesessiondata){
			File tmpDir = new File(System.getProperty("java.io.tmpdir"), "anon_backup");
			try {
				new CopyOp(new OperationI<Map<String,File>>() {
					@Override
					public void run(Map<String, File> a) throws Throwable {
						rejected[0] = anonymizer.call();
					}
				}, tmpDir,new File(sessionPath)).run();
			}catch(Exception e){
				logger.error(e.getLocalizedMessage());
				logger.error("Exception while applying Anonymization To Files",e);
			}
		}
		return rejected[0];
	}
	
	/**
	 * Gets the dicom object.
	 *
	 * @param rootpath the rootpath
	 * @param scan the scan
	 * @return the dicom object
	 */
	private DicomObject getDicomObject(String rootpath, final XnatImagescandataI scan) {
		AtomicBoolean foundDicom = new AtomicBoolean(false);
		final List<XnatResourcecatalogI> resources = scan.getFile();
		final String project = scan.getProject();
		DicomObject dicomObject = findDicomFileAndGetHeader(rootpath, resources, project, "DICOM", foundDicom);
		if (dicomObject != null) {
			return dicomObject;
		}
		if (!foundDicom.get()) {
			return findDicomFileAndGetHeader(rootpath, resources, project, "secondary", foundDicom);
		}

		return null;
	}

	/**
	 * Find single DICOM file in scan resources and read its DicomObject
	 * @param rootpath the rootpath
	 * @param resources the scan resources
	 * @param project the scan project
	 * @param desiredLabel the resource label we want to inspect for DICOM
	 * @param foundDicom field to set indicating we found a resource with desiredLabel
	 * @return the DicomObject or null if no DICOM files found
	 */
	@Nullable
	private DicomObject findDicomFileAndGetHeader(String rootpath,
												  List<XnatResourcecatalogI> resources,
												  String project,
												  String desiredLabel,
												  AtomicBoolean foundDicom) {
		for (XnatResourcecatalogI resource : resources) {

			final String type = resource.getLabel();
			if (desiredLabel.equals(type)) {
				foundDicom.set(true);

				CatalogUtils.CatalogData catalogData;
				try {
					catalogData = CatalogUtils.CatalogData.getOrCreate(rootpath, resource, project);
				} catch (ServerException e) {
					logger.warn("Unable to ready catalog data for resource {}", resource.getXnatAbstractresourceId());
					continue;
				}
				for (CatEntryI entry : catalogData.catBean.getEntries_entry()) {
					File file = CatalogUtils.getFile(entry, catalogData.catPath, project);
					if (file != null) {
						try {
							return this.getHeader(file);
						} catch (IOException e) {
							// Ignore, not DICOM
						}
					}
				}
			}
		}

		return null;
	}

	/**
	 * Read DICOM file.
	 *
	 * @param f the f
	 * @return the header
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws FileNotFoundException the file not found exception
	 */
	DicomObject getHeader(File f) throws IOException, FileNotFoundException {
		System.out.println("Reading file " + f.getAbsolutePath());
		IOException ioexception = null;
		final DicomInputStream dis = new DicomInputStream(f);
		try {
			return dis.readDicomObject();
		} catch (IOException e) {
			throw ioexception = e;
		} finally {
			try {
				dis.close();
			} catch (IOException e) {
				if (null != ioexception) {
					logger.error("unable to close DicomInputStream", e);
					throw ioexception;
				} else {
					throw e;
				}
			}
		}
	}

	/**
	 * Apply anonymization to xml.
	 *
	 * @param session the session
	 * @throws UnknownPrimaryProjectException the unknown primary project exception
	 */
	private void applyAnonymizationToXml(XnatImagesessiondata session, String filepath) throws UnknownPrimaryProjectException {
		List<File> files = new ArrayList<File>();
		Map<String, String> header = new HashMap<String, String>();
		for (final XnatImagescandataI scan : ((XnatImagesessiondata) session).getScans_scan()) {
			DicomObject dcm = this.getDicomObject(filepath, scan);
			this.populateTags(header, dcm);
			updateScan(scan, header);
		}
		updateSession(session, header);
		removePrearchive(session);
	}

	/**
	 * Removes the prearchive.
	 *
	 * @param session the session
	 */
	private void removePrearchive(XnatImagesessiondata session) {
		session.setPrearchivepath(null);

	}

	/**
	 * Update session.
	 *
	 * @param session the session
	 * @param header the header
	 */
	private void updateSession(XnatImagesessiondata session, Map<String, String> header) {
		//test
		session.setUid((header.get(StudyInstanceUID) != null) ? header.get(StudyInstanceUID) : "");
		//session.setDate((header.get(ScanDate)!=null)?header.get(ScanDate):DateDefault);

		session.setDcmaccessionnumber((header.get(AccessionNumber) != null) ? header.get(AccessionNumber) : "");
		session.setDcmpatientname((header.get(PatientName) != null) ? header.get(PatientName) : "");
		session.setDcmpatientid((header.get(PatientID) != null) ? header.get(PatientID) : "");
		//session.setDcmpatientbirthdate((header.get(PatientBirthDate)!=null)?header.get(PatientBirthDate):"");
	}

	/**
	 * Update scan.
	 *
	 * @param scan the scan
	 * @param header the header
	 */
	private void updateScan(XnatImagescandataI scan, Map<String, String> header) {
		scan.setUid((header.get(SeriesInstanceUID) != null) ? header.get(SeriesInstanceUID) : "");
	}

	/**
	 * Populate tags.
	 *
	 * @param header the header
	 * @param dcmObj the dcm obj
	 */
	private void populateTags(Map<String, String> header, DicomObject dcmObj) {
		System.out.println("dcmObj is null " + dcmObj==null?"Yes":"Not null");
		if (dcmObj != null) {
			header.put(StudyInstanceUID, dcmObj.getString(Tag.StudyInstanceUID));
			header.put(AccessionNumber, dcmObj.getString(Tag.AccessionNumber));
			header.put(PatientName, dcmObj.getString(Tag.PatientName));
			header.put(PatientID, dcmObj.getString(Tag.PatientID));
			header.put(PatientBirthDate, dcmObj.getString(Tag.PatientBirthDate));
			header.put(SeriesInstanceUID, dcmObj.getString(Tag.SeriesInstanceUID));
			header.put(ScanDate, dcmObj.getString(Tag.Date));
			System.out.println("DCMObj: " + dcmObj.getString(Tag.SeriesInstanceUID));

		}
		System.out.println("header.get(SeriesInstanceUID): " + header.get(SeriesInstanceUID));
		return;
	}

	/** The Constant logger. */
	private final static Logger logger = LoggerFactory.getLogger(XsyncAnonymizer.class);

}
