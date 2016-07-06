package org.nrg.xnat.restlet.extensions.xsync;


import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mohana Ramaratnam
 *
 */
@XnatRestlet({ "/xsync/remoteREST" })
public class XsyncRemoteRestRestlet extends SecureResource {

	JsonNode jsonNode = null;
	static final String JSON_URL = "url"; 
	static final String JSON_METHOD = "method"; 
	static final String JSON_USER = "user"; 
	static final String JSON_PASSWORD = "password"; 
	static final Logger logger = Logger.getLogger(XsyncRemoteRestRestlet.class);
	
	public XsyncRemoteRestRestlet(Context context, Request request, Response response) throws ResourceException {
		super(context, request, response);
	}

	@Override
	public boolean allowPost() {
		return true;
	}

	@Override
	public void handlePost() {
		try {
			final String jsonbody = this.getRequest().getEntity().getText();
			final ObjectMapper objectMapper = new ObjectMapper();
			jsonNode = objectMapper.readValue(jsonbody, JsonNode.class);
			final String jsonUrl = jsonNode.get(JSON_URL).asText();
			final String method = jsonNode.get(JSON_METHOD).asText();
			final String user = jsonNode.get(JSON_USER).asText();
			final String password = jsonNode.get(JSON_PASSWORD).asText();
			
			
            final URL url = new URL (jsonUrl);
            final byte[] encoding = Base64.encodeBase64((user + ":" + password).getBytes());

            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setDoOutput(true);
            connection.setRequestProperty  ("Authorization", "Basic " + new String(encoding, "UTF-8"));
            final InputStream content = (InputStream)connection.getInputStream();
            final String results = IOUtils.toString(content, "UTF-8");
            content.close();
			getResponse().setEntity(new StringRepresentation(results));
			
		}catch (Exception  exception) {
			
			logger.error("XSync remote REST exception:  " + exception.getMessage());
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, exception, exception.getMessage());
			
		}
	}
	
}