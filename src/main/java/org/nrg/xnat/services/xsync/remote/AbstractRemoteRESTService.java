package org.nrg.xnat.services.xsync.remote;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.nrg.xsync.connection.RemoteConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mohana Ramaratnam
 *
 */
public abstract class AbstractRemoteRESTService {
	//@Autowired
		//SimpleClientHttpRequestFactory requestFactory;

		/** The logger. */
		public static Logger logger = Logger.getLogger(AbstractRemoteRESTService.class);

		/**
		 * Gets the resttemplate.
		 *
		 * @return the resttemplate
		 */
		public RestTemplate getResttemplate(){
			SimpleClientHttpRequestFactory requestFactory =new SimpleClientHttpRequestFactory();
			//requestFactory.setBufferRequestBody(false);
			return new RestTemplate(requestFactory);
		}


}
