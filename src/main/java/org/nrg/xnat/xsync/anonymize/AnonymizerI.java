package org.nrg.xnat.xsync.anonymize;

/**
 * @author Mohana Ramaratnam
 *
 */
import org.nrg.xdat.om.XnatImagesessiondata;


public interface AnonymizerI {
	void anonymize(XnatImagesessiondata session, String destProject, String cacheSessionPath) throws Exception;
}
