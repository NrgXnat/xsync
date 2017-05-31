package org.nrg.xnat.xsync.anonymize;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.nrg.dcm.CopyOp;
import org.nrg.transaction.OperationI;
import org.nrg.transaction.TransactionException;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.CatEntryI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatResourcecatalogI;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.base.BaseXnatExperimentdata.UnknownPrimaryProjectException;
import org.nrg.xnat.utils.CatalogUtils;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

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
	public void anonymize(final XnatImagesessiondata session, final String destproject) throws Exception {
		//String exptCachePath = SynchronizationManager.GET_SYNC_FILE_PATH(session.getProject());
		String exptCachePath =  SynchronizationManager.GET_SYNC_FILE_PATH_TO_SESSION(session.getProject(),session) ;
		try {
			//File cachePath = new File(exptCachePath + File.separator + "ARCHIVECOPY" + File.separator + session.getLabel() + File.separator);
			File cachePath = new File(exptCachePath);
			//FileUtils.copyDirectoryToDirectory(session.getSessionDir(), cachePath);
			//Smart copy - dont copy all the data; only what is needed
			//copyScanFiles(session,cachePath);
			ExportAnonymizer anonymizer = new ExportAnonymizer(_xsyncXnatInfo, session, destproject, cachePath.getAbsolutePath());
			this.applyAnonymizationToFiles(session,cachePath.getAbsolutePath(),anonymizer);
		} catch (TransactionException e) {
			logger.error("applyAnonymizationToFiles", e);
			throw new Exception(e);
		}
		try {
			this.applyAnonymizationToXml(session,exptCachePath);
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
	public void applyAnonymizationToFiles(final XnatImagesessiondata session,String sessionPath, final ExportAnonymizer anonymizer) throws TransactionException{
		if(session instanceof XnatImagesessiondata){
			File tmpDir = new File(System.getProperty("java.io.tmpdir"), "anon_backup");
			try {
				new CopyOp(new OperationI<Map<String,File>>() {
					@Override
					public void run(Map<String, File> a) throws Throwable {
						anonymizer.call();
					}
				}, tmpDir,new File(sessionPath)).run();
			}catch(Exception e){
				logger.error(e.getLocalizedMessage());
			}
		}
	}
	
	/**
	 * Gets the dicom object.
	 *
	 * @param rootpath the rootpath
	 * @param scan the scan
	 * @return the dicom object
	 */
	private DicomObject getDicomObject(String rootpath, final XnatImagescandataI scan) {
		final List<XnatResourcecatalogI> resources = scan.getFile();

		final List<File> files = Lists.newArrayList();
		boolean foundDicom = false;
		for (XnatResourcecatalogI resource : resources) {

			final String type = resource.getLabel();
			if ("DICOM".equals(type)) {
				foundDicom = true;
				final File catalogFile = CatalogUtils.getCatalogFile(rootpath, resource);
				CatCatalogBean cat = CatalogUtils.getCatalog(catalogFile);
				for (CatEntryI match : CatalogUtils.getEntriesByRegex(cat, ".*.dcm")) {
					String parentPath = (new File(resource.getUri())).getParent();
					files.add(CatalogUtils.getFile(match, parentPath));

					if (files.size() >= 1) {
						DicomObject dcmObject = null;
						try {
							dcmObject = this.getHeader(files.get(0));
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return dcmObject;
					}
				}
			}
		}
		//If no DICOMs exist, check to see if secondary image exists
		if (!foundDicom) {
			for (XnatResourcecatalogI resource : resources) {

				final String type = resource.getLabel();
				if ("secondary".equals(type)) {
					final File catalogFile = CatalogUtils.getCatalogFile(rootpath, resource);
					CatCatalogBean cat = CatalogUtils.getCatalog(catalogFile);
					for (CatEntryI match : CatalogUtils.getEntriesByRegex(cat, ".*.dcm")) {
						String parentPath = (new File(resource.getUri())).getParent();
						files.add(CatalogUtils.getFile(match, parentPath));

						if (files.size() >= 1) {
							DicomObject dcmObject = null;
							try {
								dcmObject = this.getHeader(files.get(0));
							} catch (FileNotFoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							return dcmObject;
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
