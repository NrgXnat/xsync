package org.nrg.xnat.xsync.anonymize;

/**
 * @author Mohana Ramaratnam
 *
 */
import java.util.List;
import org.nrg.xdat.om.XnatImagesessiondata;


public interface AnonymizerI {
	List<AnonScanResult> anonymize(XnatImagesessiondata session, String destProject, String cacheSessionPath) throws Exception;
}
