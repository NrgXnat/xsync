package org.nrg.xsync.services.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public abstract class AbstractRemoteRESTService {

		/** The logger. */
		public static final Logger logger = LoggerFactory.getLogger(AbstractRemoteRESTService.class);

		/**
		 * Gets the resttemplate.
		 *
		 * @return the resttemplate
		 */
		public RestTemplate getResttemplate(){
			final SimpleClientHttpRequestFactory requestFactory =new SimpleClientHttpRequestFactory();
			requestFactory.setBufferRequestBody(false);
			final RestTemplate template = new RestTemplate(requestFactory); 
			template.setErrorHandler(new XsyncResponseErrorHandler());
			return template;
		}
		
}
