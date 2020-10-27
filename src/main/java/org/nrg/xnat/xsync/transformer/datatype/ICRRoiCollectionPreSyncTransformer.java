package org.nrg.xnat.xsync.transformer.datatype;
import java.util.Map;

import org.nrg.xdat.om.IcrRoicollectiondata;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xnat.xsync.annotation.DatatypeTransformerAnnotation;
import org.nrg.xnat.xsync.transformer.SyncTransformerI;
import org.nrg.xnat.xsync.transformer.TransformerHelper;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Mohana Ramaratnam (mohanakannan9@gmail.com)
 * 
 * This class is responsible for modifying the fields within the icr:ROICollection
 * to replace subject_id and other fields.
 *
 */
@Component
@DatatypeTransformerAnnotation(xsiType="icr:roiCollectionData")
@Slf4j
public class ICRRoiCollectionPreSyncTransformer implements SyncTransformerI {

private final String myXsiType = "icr:roiCollectionData";

    public void transform(XnatExperimentdata exp, Map<String, String> attributes) {
        if (myXsiType.equals(exp.getXSIType())) {
            //Your action to replace values required from attributes
            //The keys in the attributes are from the class
            //org.nrg.xnat.xsync.transformer.TransformerHelper
        	IcrRoicollectiondata icrROI = (IcrRoicollectiondata) exp;
        	String remoteSubjectID = attributes.get(TransformerHelper.REMOTE_SUBJECT_ID);
        	icrROI.setSubjectid(remoteSubjectID);
        }
    }
}