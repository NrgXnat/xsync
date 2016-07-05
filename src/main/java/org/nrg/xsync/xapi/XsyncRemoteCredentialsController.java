package org.nrg.xsync.xapi;

import java.util.Date;

import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xdat.rest.AbstractXnatRestApi;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
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
	        	return new ResponseEntity<>("Could not save remote credentials.  Incomplete information supplied.", HttpStatus.INTERNAL_SERVER_ERROR );
	        	
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

}
