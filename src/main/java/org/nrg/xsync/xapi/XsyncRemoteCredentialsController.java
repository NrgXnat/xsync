package org.nrg.xsync.xapi;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.rest.AbstractXnatRestApi;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.restlet.resource.StringRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * The Class XsyncPreferencesController.
 *
 * @author Mike Hodge
 */

@XapiRestController
@RequestMapping(value = "/xsync")
@Api(description = "XSync Credentials API")
public class XsyncRemoteCredentialsController extends AbstractXnatRestApi {
	
	@Autowired
	private RemoteAliasService _remoteAliasService;

	/**
	 * Saves the remote credentials
	 *
	 * @param jsonbody the jsonbody
	 * @return the response entity
	 */
	@RequestMapping(path="/projects/{projectId}/saveRemoteCredentials", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Sets remote crecentials for XSync")
    @ApiResponses({@ApiResponse(code = 200, message = "XSync remote credentials set."),  @ApiResponse(code = 500, message = "Unexpected error")})
	public synchronized ResponseEntity<String> saveRemoteCredentials(@RequestBody String jsonbody) {
		try {
			final ObjectMapper objectMapper = new ObjectMapper();
			final JsonNode synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);
	        final String host = (synchronizationJson.get("host")!=null) ? synchronizationJson.get("host").asText() : null;
	        final String alias = (synchronizationJson.get("alias")!=null) ? synchronizationJson.get("alias").asText() : null;
	        final String secret = (synchronizationJson.get("secret")!=null) ? synchronizationJson.get("secret").asText() : null;
	        final String localProject = (synchronizationJson.get("localProject")!=null) ? synchronizationJson.get("localProject").asText() : null;
	        if (host==null || host.length()<1 || alias==null || alias.length()<1 || secret==null ||
	        		secret.length()<1 || localProject==null || localProject.length()<1) {
	        	return new ResponseEntity<>("Could not save remote credentials.  Incomplete information supplied.", HttpStatus.BAD_REQUEST );
	        	
	        }
	        RemoteAliasEntity remoteAliasEntity = _remoteAliasService.getRemoteAliasEntity(localProject, host);
	        if (remoteAliasEntity != null) {
	        	remoteAliasEntity.setRemote_alias_token(alias);
	        	remoteAliasEntity.setRemote_alias_password(secret);
	        	_remoteAliasService.update(remoteAliasEntity);
	        } else {
	        	remoteAliasEntity = new RemoteAliasEntity();
	        	remoteAliasEntity.setLocal_project(localProject);
	        	remoteAliasEntity.setRemote_host(host);
	        	remoteAliasEntity.setRemote_alias_token(alias);
	        	remoteAliasEntity.setRemote_alias_password(secret);
	        	final Date now = new Date();
	        	remoteAliasEntity.setAcquiredTime(now);
	        	_remoteAliasService.create(remoteAliasEntity);
	        }
		}catch (Exception  exception) {
        	return new ResponseEntity<>("XSync saving of remote credentials failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
       	return new ResponseEntity<>("XSync remote credentials set", HttpStatus.OK );
	}

	/**
	 * Checks the stored remote credentials
	 *
	 * @param jsonbody the jsonbody
	 * @return the response entity
	 */
	@RequestMapping(path="/projects/{projectId}/checkRemoteCredentials", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Checks whether XSync remote credentials are valid")
    @ApiResponses({@ApiResponse(code = 200, message = "Remote credentials valid."),  @ApiResponse(code = 500, message = "Unexpected error")})
	public synchronized ResponseEntity<String> checkRemoteCredentials(@RequestBody String jsonbody) {
		try {
			final ObjectMapper objectMapper = new ObjectMapper();
			final JsonNode synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);
	        final String host = (synchronizationJson.get("host")!=null) ? synchronizationJson.get("host").asText() : null;
	        final String localProject = (synchronizationJson.get("localProject")!=null) ? synchronizationJson.get("localProject").asText() : null;
	        if (host==null || host.length()<1 || localProject==null || localProject.length()<1) {
	        	return new ResponseEntity<>("Could not check remote credentials.  Incomplete information supplied.", HttpStatus.BAD_REQUEST );
	        }
	        RemoteAliasEntity remoteAliasEntity = _remoteAliasService.getRemoteAliasEntity(localProject, host);
			
	        try {
	        	final URL url = new URL (host + "/data/JSESSIONID");
	        	final byte[] encoding = Base64.encodeBase64((remoteAliasEntity.getRemote_alias_token() + ":" + remoteAliasEntity.getRemote_alias_password()).getBytes());
	        	final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        	String method;
	        	connection.setRequestMethod("GET");
	        	connection.setDoOutput(true);
	        	connection.setRequestProperty  ("Authorization", "Basic " + new String(encoding, "UTF-8"));
	        	final InputStream content = (InputStream)connection.getInputStream();
	        	final String results = IOUtils.toString(content, "UTF-8");
	        	content.close();
	        } catch (Exception e) {
	        	return new ResponseEntity<>("Could not connect", HttpStatus.BAD_REQUEST);
	        }
	        
		}catch (Exception  exception) {
        	return new ResponseEntity<>("Could not connect", HttpStatus.INTERNAL_SERVER_ERROR );
		}
       	return new ResponseEntity<>("Connected to remote host", HttpStatus.OK );
	}

}
