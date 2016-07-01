package org.nrg.xsync.services.remote;

import org.apache.log4j.Logger;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public abstract class AbstractRemoteRESTService {

		/** The logger. */
		public static final Logger logger = Logger.getLogger(AbstractRemoteRESTService.class);

		/**
		 * Gets the resttemplate.
		 *
		 * @return the resttemplate
		 */
		public RestTemplate getResttemplate(){
			final SimpleClientHttpRequestFactory requestFactory =new SimpleClientHttpRequestFactory();
			final RestTemplate template = new RestTemplate(requestFactory); 
			template.setErrorHandler(new XsyncResponseErrorHandler());
			return template;
		}
		
}
