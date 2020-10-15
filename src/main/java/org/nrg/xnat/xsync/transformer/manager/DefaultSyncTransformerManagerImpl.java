package org.nrg.xnat.xsync.transformer.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nrg.xdat.XDAT;
import org.nrg.xnat.xsync.annotation.DatatypeTransformerAnnotation;
import org.nrg.xnat.xsync.transformer.SyncTransformerI;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Mohana Ramaratnam (mohanakannan9@gmail.com)
 * 
 * This class would return a transformer based on the XSIType of the object
 *
 */

@Slf4j
@Component
public class DefaultSyncTransformerManagerImpl implements SyncTransformerManagerI {

	public SyncTransformerI getSyncTransformer(String xsiType) {
		SyncTransformerI syncTransformer = null;
		List<SyncTransformerI> transformers = null;

		try {
	        Map<String, SyncTransformerI> serviceMap =  XDAT.getContextService().getBeansOfType(SyncTransformerI.class);
	        if (serviceMap != null) {
	        	transformers = new ArrayList<>(serviceMap.values());
	        }
	    } catch (Exception e) {
	        log.error("Unable to retrieve injected Sync Transformers beans", e);
	    }

	    if (transformers == null || transformers.isEmpty()) {
	        log.trace("No Sync Transformer beans");
	    }

	    for (SyncTransformerI st : transformers) {
	        final DatatypeTransformerAnnotation annotation = st.getClass().getAnnotation(DatatypeTransformerAnnotation.class);
	        if (annotation != null) {
	        	if (xsiType.equals(annotation.xsiType())) {
	        		syncTransformer = st;
	        		break;
	        	}
	        }

	    }
		return syncTransformer;
	}
}
