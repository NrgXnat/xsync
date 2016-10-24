package org.nrg.xsync.xapi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.rest.AbstractXapiRestController;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.utils.XsyncUtils;
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
@RequestMapping(value = "/xsync/credentials")
@Api(description = "XSync Credentials API")
public class XsyncRemoteCredentialsController extends AbstractXapiRestController {
	
	private final RemoteAliasService 		_remoteAliasService;
	private final ConfigService              _configService;
	private final SerializerService          _serializer;
	


	@Autowired
	public XsyncRemoteCredentialsController(final RemoteAliasService remoteAliasService, final UserManagementServiceI userManagementService, final RoleHolder roleHolder,final ConfigService configService,final SerializerService serializer) {
        super(userManagementService, roleHolder);
        _remoteAliasService = remoteAliasService;
		_configService = configService;
		_serializer = serializer;
	}

	
	/**
	 * Saves the remote credentials
	 *
	 * @param jsonbody the jsonbody
	 * @return the response entity
	 */
	@RequestMapping(path="/save/projects/{projectId}", method = RequestMethod.POST, consumes = MediaType.TEXT_PLAIN_VALUE)
    @ApiOperation(value = "Sets remote credentials for XSync")
    @ApiResponses({@ApiResponse(code = 200, message = "XSync remote credentials set."),  @ApiResponse(code = 500, message = "Unexpected error")})
	public synchronized ResponseEntity<String> saveRemoteCredentials(@RequestBody String jsonbody) {
		ResponseEntity<String> response;
		String message;
		try {
			final ObjectMapper objectMapper = new ObjectMapper();
			final JsonNode synchronizationJson = objectMapper.readValue(jsonbody, JsonNode.class);
	        final String host = (synchronizationJson.get("host")!=null) ? synchronizationJson.get("host").asText() : null;
	        final String alias = (synchronizationJson.get("alias")!=null) ? synchronizationJson.get("alias").asText() : null;
	        final String secret = (synchronizationJson.get("secret")!=null) ? synchronizationJson.get("secret").asText() : null;
	        final String localProject = (synchronizationJson.get("localProject")!=null) ? synchronizationJson.get("localProject").asText() : null;
	        final String username = (synchronizationJson.get("username")!=null) ? synchronizationJson.get("username").asText() : null;
			final String remoteProjectId = (synchronizationJson.get("remoteProject")!=null) ? synchronizationJson.get("remoteProject").asText() : null;
			//Only XNAT 1.7 will supply the expiration time. If the Destination server is running on < XNAT 1.7, this value would be null
			final String estimatedExpirationTime = (synchronizationJson.get("estimatedExpirationTime")!=null) ? synchronizationJson.get("estimatedExpirationTime").asText() : null;
			final boolean syncNewOnly = synchronizationJson.get("syncNewOnly") == null || synchronizationJson.get("syncNewOnly").asBoolean();

	        if (host==null || host.length()<1 || alias==null || alias.length()<1 || secret==null ||
	        		secret.length()<1 || localProject==null || localProject.length()<1 || username==null || username.length()<1) {
	        	return new ResponseEntity<>("Could not save remote credentials.  Incomplete information supplied.", HttpStatus.BAD_REQUEST );
	        	
	        }

	        final String userAccessUrl = (host.endsWith("/")? host:host+"/") + "data/archive/projects/"+remoteProjectId+"/users?format=json";
	        response = userHasRequiredAccessAtRemoteProject(alias,secret,username,userAccessUrl,remoteProjectId,syncNewOnly);
	        
	        if (response != null && (response.getStatusCode().value() == HttpStatus.OK.value() || response.getStatusCode().value() == HttpStatus.ACCEPTED.value() )) {
				RemoteAliasEntity remoteAliasEntity = _remoteAliasService.getRemoteAliasEntity(localProject, host);
		        if (remoteAliasEntity != null) {
		        	remoteAliasEntity.setRemote_alias_token(alias);
		        	remoteAliasEntity.setRemote_alias_password(secret);
		        	if (estimatedExpirationTime != null) {
			        	try {
			        		//1.7.1+ sends the estimatedExpirationTime like so
			        		remoteAliasEntity.setEstimatedExpirationTime(new Date(Long.parseLong(estimatedExpirationTime)));
			        	}catch(Exception e) {
			        		try {
			        			//1.6.5 may not send at all and 1.7.0 sends it in this format.
			        			 DateFormat format = new SimpleDateFormat("YYYYMMDD_HHmmss");
			        			 remoteAliasEntity.setEstimatedExpirationTime(format.parse(estimatedExpirationTime));
			        		}catch(Exception e1){}
			        	}
		        	}
		        	_remoteAliasService.update(remoteAliasEntity);
		        } else {
		        	remoteAliasEntity = new RemoteAliasEntity();
		        	remoteAliasEntity.setLocal_project(localProject);
		        	remoteAliasEntity.setRemote_host(host);
		        	remoteAliasEntity.setRemote_alias_token(alias);
		        	remoteAliasEntity.setRemote_alias_password(secret);
		        	final Date now = new Date();
		        	remoteAliasEntity.setAcquiredTime(now);
			        	if (estimatedExpirationTime != null) {
				        	try {
				        		//1.7.1+ sends the estimatedExpirationTime like so
				        		remoteAliasEntity.setEstimatedExpirationTime(new Date(Long.parseLong(estimatedExpirationTime)));
				        	}catch(Exception e) {
				        		try {
				        			//1.6.5 may not send at all and 1.7.0 sends it in this format.
				        			 DateFormat format = new SimpleDateFormat("YYYYMMDD_HHmmss");
				        			 remoteAliasEntity.setEstimatedExpirationTime(format.parse(estimatedExpirationTime));
				        		}catch(Exception e1){}
				        		
				        	}
			        	}
		        	_remoteAliasService.create(remoteAliasEntity);
		        }
				if (response != null && response.getStatusCode().value() == HttpStatus.ACCEPTED.value()) {
					message = "User  " + username + " does not have Owner level access to " + remoteProjectId + ".";
					message += " Data Deletion will fail if the user has Member level access.";
		           	return new ResponseEntity<>(message, HttpStatus.ACCEPTED);
				}
	        }else {
	           	if (response != null)
	           		return response;
	           	else
	           		return new  ResponseEntity<>("XSync saving of remote credentials failed ", HttpStatus.BAD_REQUEST);
	        }
		}catch (Exception  exception) {
			exception.printStackTrace();
        	return new ResponseEntity<>("XSync saving of remote credentials failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
		return new ResponseEntity<>("XSync remote credentials set.", HttpStatus.OK );
	}
	
	private ResponseEntity<String> userHasRequiredAccessAtRemoteProject(String alias, String secret,String username,String urlStr,String remoteProjectId, boolean syncNewOnly) {
		boolean found = false;
		boolean permitted = false; //Hack for users with Allow "All Data Access"
		try {
        	final URL url = new URL (urlStr);
        	final byte[] encoding = Base64.encodeBase64((alias + ":" + secret).getBytes());
        	final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
        	connection.setDoOutput(true);
        	connection.setRequestProperty  ("Authorization", "Basic " + new String(encoding, "UTF-8"));
        	//Only Owners and Site Managers can access project level user resources
        	try (final InputStream content = connection.getInputStream()) {
        		permitted  = true;
				final String results = IOUtils.toString(content, "UTF-8");
        		final JsonNode userJsonRoot = _serializer.deserializeJson(results, JsonNode.class);
        		final JsonNode userJsonNode = userJsonRoot.get("ResultSet");
        		final JsonNode usersNode = userJsonNode.get("Result");
        		if (usersNode.isArray()) {
        			for (JsonNode u:usersNode) {
        				if (u.get(XsyncUtils.USER_API_LOGIN).asText().equals(username)) {
        					final String accessLevel = u.get(XsyncUtils.USER_API_GROUP_ID).asText();
        					if (accessLevel.endsWith("_"+XsyncUtils.USER_ACCESS_MEMBER)) {
        			           	return new ResponseEntity<>("User  " + username + " has Member level access", HttpStatus.ACCEPTED);
        					}else if (accessLevel.endsWith("_"+XsyncUtils.USER_ACCESS_COLLABORATOR)) {
        			           	return new ResponseEntity<>("User  " + username + " has Collaborator level access", HttpStatus.FORBIDDEN);
        					}else if (accessLevel.endsWith("_"+XsyncUtils.USER_ACCESS_OWNER)) {
        						return new ResponseEntity<>("User " + username + " has CRUD access.", HttpStatus.OK);
        					}
        					found = true;
        					break;
        				}
        			}
        		}
			}catch(FileNotFoundException fne) {
		    	 return new ResponseEntity<>("User does not have access to the project " + remoteProjectId, HttpStatus.FORBIDDEN);
			}catch (IOException ioe) {
			    	 return new ResponseEntity<>("User " + username + " probably has Collaborator level access. Xsync will fail.", HttpStatus.FORBIDDEN);
			}catch(Exception e) {
			         return new ResponseEntity<>("Could not connect", HttpStatus.BAD_REQUEST);
	        }
        } catch (Exception e) {
        	return new ResponseEntity<>("Could not connect", HttpStatus.BAD_REQUEST);
        }
		if (!found) {
			if (permitted) {
	           	return new ResponseEntity<>("User  " + username + " may have All Data Access.", HttpStatus.ACCEPTED);
			}else
				return new ResponseEntity<>("User  " + username + " has no access", HttpStatus.FORBIDDEN);
		}else {
           	return new ResponseEntity<>("User  " + username + " has access.", HttpStatus.ACCEPTED);
		}
	}

	/**
	 * Checks the stored remote credentials
	 *
	 * @param jsonbody the jsonbody
	 * @return the response entity
	 */
	@RequestMapping(path="/check/projects/{projectId}", method = RequestMethod.POST, consumes = MediaType.TEXT_PLAIN_VALUE)
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
				connection.setRequestMethod("GET");
	        	connection.setDoOutput(true);
	        	connection.setRequestProperty  ("Authorization", "Basic " + new String(encoding, "UTF-8"));
	        	try (final InputStream content = connection.getInputStream()) {
					final String results = IOUtils.toString(content, "UTF-8");
					return new ResponseEntity<>(results, HttpStatus.OK);
				}
	        } catch (Exception e) {
	        	return new ResponseEntity<>("Could not connect", HttpStatus.BAD_REQUEST);
	        }
	        
		}catch (Exception exception) {
        	return new ResponseEntity<>("Could not connect", HttpStatus.INTERNAL_SERVER_ERROR );
		}
	}

	
	
}
