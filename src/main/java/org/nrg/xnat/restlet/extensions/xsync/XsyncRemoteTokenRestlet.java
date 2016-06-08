package org.nrg.xnat.restlet.extensions.xsync;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.nrg.xsync.utils.LineStreamConsumer;
import org.nrg.xsync.utils.ScriptResult;
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
@XnatRestlet({ "/xsync/remoteToken" })
public class XsyncRemoteTokenRestlet extends SecureResource {

	JsonNode tokenJson = null;
	static final String JSON_HOST = "host"; 
	static final String JSON_USER = "user"; 
	static final String JSON_PASSWORD = "password"; 
	
	public XsyncRemoteTokenRestlet(Context context, Request request, Response response) throws ResourceException {
		super(context, request, response);
	}

	@Override
	public boolean allowPost() {
		return true;
	}

	@Override
	public void handlePost() {
		try {
			String jsonbody = this.getRequest().getEntity().getText();
			ObjectMapper objectMapper = new ObjectMapper();
			tokenJson = objectMapper.readValue(jsonbody, JsonNode.class);
			final String host = tokenJson.get(JSON_HOST).asText();
			final String user = tokenJson.get(JSON_USER).asText();
			final String password = tokenJson.get(JSON_PASSWORD).asText();
			ScriptResult sResult = execRuntimeCommand("curl -s -k -u " + user + ":" + password + " -X GET " + host + "/data/services/tokens/issue/user/" + user);
			getResponse().setEntity(new StringRepresentation(getResultListAsString(sResult.getResultList())));
		}catch (Exception  exception) {
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, exception, exception.getMessage());
		}
	}
	
	private String getResultListAsString(List<String> resultList) {
		StringBuilder sb = new StringBuilder();
		for (String s : resultList) {
			sb.append(s);
		}
		return sb.toString();
	}

	private static ScriptResult execRuntimeCommand(String cmd) {
		Integer returnCode = null;
		boolean returnStatus = false;
		List<String> returnList = new ArrayList<String>();	
		try {
			final Process process = Runtime.getRuntime().exec(cmd);
		    final LineStreamConsumer stdout = new LineStreamConsumer(process.getInputStream()),
		    		stderr = new LineStreamConsumer(process.getErrorStream());
		    try {
		        stdout.start();
		        stderr.start();
		        if (logger.isTraceEnabled()) {
		        	String notifyStr =  String.format("executing command with stdout, stderr consumers %s %s",
		                    new Object[]{cmd.toString(), stdout, stderr});
		            logger.trace(notifyStr);
		            //returnList.add(notifyStr);
		        }
		        final int rc = process.waitFor();
		        returnCode = rc;
		        returnList.addAll(stdout.getLines());
		        returnList.addAll(stderr.getLines());
		        if (0 == rc) {
		        	returnStatus = true;
		        } else if (98 == rc) {
		        	returnList.add("ERROR:  Duplicate process detected.  May only execute one instance of this script.");
		        } else {
		        	returnList.add("Process failed.");
		        }
		    } catch (InterruptedException e) {
					e.printStackTrace();
			} finally {
		       stdout.close();
		       stderr.close();
		    }
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ScriptResult(returnStatus,returnCode,returnList);
	}
		
}