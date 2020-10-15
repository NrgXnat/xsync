package org.nrg.xnat.xsync.transformer;

import java.util.Map;

import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xnat.xsync.transformer.manager.SyncTransformerManagerI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Mohana Ramaratnam(mohanakannan9@gmail.com)
 *
 */
@Component
public class XsyncDataTypeSpecificTransformer {

	
	public void transformExperiment(XnatImagesessiondata exp, Map<String, String> attributes) {
		if (null == exp) {
			return;
		}
		String xsiType = exp.getXSIType();
		if (null == xsiType) {
			return;
		}
		
		SyncTransformerI syncTransformer = _transformerService.getSyncTransformer(xsiType);
		if (null == syncTransformer) {
			return;
		}
		syncTransformer.transform(exp, attributes);
		return;
	}

	public void transformSubjectAssessor(XnatSubjectassessordata exp, Map<String, String> attributes) {
		if (null == exp) {
			return;
		}
		String xsiType = exp.getXSIType();
		if (null == xsiType) {
			return;
		}
		
		SyncTransformerI syncTransformer = _transformerService.getSyncTransformer(xsiType);
		if (null == syncTransformer) {
			return;
		}
		syncTransformer.transform(exp, attributes);
		return;
	}

	public void transformImageAssessor(XnatImageassessordata exp, Map<String, String> attributes) {
		if (null == exp) {
			return;
		}
		String xsiType = exp.getXSIType();
		if (null == xsiType) {
			return;
		}
		
		SyncTransformerI syncTransformer = _transformerService.getSyncTransformer(xsiType);
		if (null == syncTransformer) {
			return;
		}
		syncTransformer.transform(exp, attributes);
		return;
	}
	
	
	@Autowired
	SyncTransformerManagerI _transformerService;
	
}
