package org.nrg.xsync.xapi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.services.SerializerService;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xsync.pojo.XsyncRemoteCredentialsPojo;
import org.nrg.xsync.remote.alias.RemoteAliasEntity;
import org.nrg.xsync.remote.alias.services.RemoteAliasService;
import org.nrg.xsync.security.XsyncEditProjectUserAuthority;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;

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
@Api("XSync Credentials API")
public class XsyncRemoteCredentialsController extends AbstractXapiProjectRestController {
	
	private final RemoteAliasService 		_remoteAliasService;
	private final SerializerService          _serializer;
	public static Logger _logger = LoggerFactory.getLogger(XsyncRemoteCredentialsController.class);

	@Autowired
	public XsyncRemoteCredentialsController(final RemoteAliasService remoteAliasService,
											final UserManagementServiceI userManagementService,
											final RoleHolder roleHolder,
											final SerializerService serializer) {
        super(userManagementService, roleHolder);
        _remoteAliasService = remoteAliasService;
		_serializer = serializer;
	}

	@AuthDelegate(XsyncEditProjectUserAuthority.class)
    @XapiRequestMapping(path="/save/projects/{projectId}", method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Sets remote credentials for XSync")
    @ApiResponses({@ApiResponse(code = 200, message = "XSync remote credentials set."),
			@ApiResponse(code = 500, message = "Unexpected error")})
	public synchronized ResponseEntity<String> saveRemoteCredentials(@PathVariable("projectId") String projectId,
															 @RequestBody XsyncRemoteCredentialsPojo credentialsPojo) {
		ResponseEntity<String> response;
		String message;
		try {
	        if (StringUtils.isAnyBlank(credentialsPojo.getHost(), credentialsPojo.getAlias(), credentialsPojo.getSecret(), credentialsPojo.getLocalProject(), credentialsPojo.getUsername())) {
	        	return new ResponseEntity<>("Could not save remote credentials.  Incomplete information supplied.", HttpStatus.BAD_REQUEST );
	        }

	        final String userAccessUrl = (credentialsPojo.getHost().endsWith("/")? credentialsPojo.getHost():credentialsPojo.getHost()+"/") + "data/archive/projects/"+credentialsPojo.getRemoteProject()+"/users?format=json";
	        response = userHasRequiredAccessAtRemoteProject(credentialsPojo.getAlias(),credentialsPojo.getSecret(),credentialsPojo.getUsername(),userAccessUrl,credentialsPojo.getRemoteProject());
	        
	        if (response != null && (response.getStatusCode().value() == HttpStatus.OK.value() || response.getStatusCode().value() == HttpStatus.ACCEPTED.value() )) {
				RemoteAliasEntity remoteAliasEntity = _remoteAliasService.getRemoteAliasEntity(credentialsPojo.getLocalProject(), credentialsPojo.getHost());
		        if (remoteAliasEntity != null) {
		        	remoteAliasEntity.setRemote_alias_token(credentialsPojo.getAlias());
		        	remoteAliasEntity.setRemote_alias_password(credentialsPojo.getSecret());
		        	if (credentialsPojo.getEstimatedExpirationTime() != null) {
			        	try {
			        		//1.7.1+ sends the estimatedExpirationTime like so
			        		remoteAliasEntity.setEstimatedExpirationTime(new Date(Long.parseLong(credentialsPojo.getEstimatedExpirationTime())));
			        	} catch(Exception e) {
			        		try {
			        			//1.6.5 may not send at all and 1.7.0 sends it in this format.
			        			 DateFormat format = new SimpleDateFormat("YYYYMMDD_HHmmss");
			        			 remoteAliasEntity.setEstimatedExpirationTime(format.parse(credentialsPojo.getEstimatedExpirationTime()));
			        		} catch(Exception ignored){}
			        	}
		        	}
		        	_remoteAliasService.update(remoteAliasEntity);
		        } else {
		        	remoteAliasEntity = new RemoteAliasEntity();
		        	remoteAliasEntity.setLocal_project(credentialsPojo.getLocalProject());
		        	remoteAliasEntity.setRemote_host(credentialsPojo.getHost());
		        	remoteAliasEntity.setRemote_alias_token(credentialsPojo.getAlias());
		        	remoteAliasEntity.setRemote_alias_password(credentialsPojo.getSecret());
		        	final Date now = new Date();
		        	remoteAliasEntity.setAcquiredTime(now);
					if (credentialsPojo.getEstimatedExpirationTime() != null) {
						try {
							//1.7.1+ sends the estimatedExpirationTime like so
							remoteAliasEntity.setEstimatedExpirationTime(new Date(Long.parseLong(credentialsPojo.getEstimatedExpirationTime())));
						} catch(Exception e) {
							try {
								//1.6.5 may not send at all and 1.7.0 sends it in this format.
								 DateFormat format = new SimpleDateFormat("YYYYMMDD_HHmmss");
								 remoteAliasEntity.setEstimatedExpirationTime(format.parse(credentialsPojo.getEstimatedExpirationTime()));
							} catch(Exception ignored){}

						}
					}
		        	_remoteAliasService.create(remoteAliasEntity);
		        }
				if (response != null && response.getStatusCode().value() == HttpStatus.ACCEPTED.value()) {
					message = "User " + credentialsPojo.getUsername() + " does not have Owner level access to " + credentialsPojo.getRemoteProject() + ".";
					message += " Data Deletion will fail if the user has Member level access.";
		           	return new ResponseEntity<>(message, HttpStatus.ACCEPTED);
				}
	        }else {
                return Objects.requireNonNullElseGet(response, () -> new ResponseEntity<>("XSync saving of remote credentials failed ", HttpStatus.BAD_REQUEST));
	        }
		} catch (Exception  exception) {
            _logger.error("ERROR:  Saving of remote credentials failed {}", ExceptionUtils.getFullStackTrace(exception));
        	return new ResponseEntity<>("XSync saving of remote credentials failed ", HttpStatus.INTERNAL_SERVER_ERROR );
		}
		return new ResponseEntity<>("XSync remote credentials set.", HttpStatus.OK );
	}
	
	private ResponseEntity<String> userHasRequiredAccessAtRemoteProject(String alias, String secret, String username,
																		String urlStr, String remoteProjectId) {
		boolean found = false;
		boolean permitted = false; //Hack for users with Allow "All Data Access"
		try {
        	final URL url = new URL (urlStr);
        	final byte[] encoding = Base64.encodeBase64((alias + ":" + secret).getBytes());
        	final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
        	connection.setDoOutput(true);
        	connection.setRequestProperty  ("Authorization", "Basic " + new String(encoding, StandardCharsets.UTF_8));
        	//Only Owners and Site Managers can access project level user resources
        	try (final InputStream content = connection.getInputStream()) {
        		permitted  = true;
				final String results = IOUtils.toString(content, StandardCharsets.UTF_8);
        		final JsonNode userJsonRoot = _serializer.deserializeJson(results, JsonNode.class);
        		final JsonNode userJsonNode = userJsonRoot.get("ResultSet");
        		final JsonNode usersNode = userJsonNode.get("Result");
        		if (usersNode.isArray()) {
        			for (JsonNode u:usersNode) {
        				if (u.get(XsyncUtils.USER_API_LOGIN).asText().equals(username)) {
        					final String accessLevel = u.get(XsyncUtils.USER_API_GROUP_ID).asText();
        					if (accessLevel.endsWith("_"+XsyncUtils.USER_ACCESS_MEMBER)) {
        			           	return new ResponseEntity<>("User  " + username + " has Member level access", HttpStatus.ACCEPTED);
        					} else if (accessLevel.endsWith("_"+XsyncUtils.USER_ACCESS_COLLABORATOR)) {
        			           	return new ResponseEntity<>("User  " + username + " has Collaborator level access", HttpStatus.FORBIDDEN);
        					} else if (accessLevel.endsWith("_"+XsyncUtils.USER_ACCESS_OWNER)) {
        						return new ResponseEntity<>("User " + username + " has CRUD access.", HttpStatus.OK);
        					}
        					found = true;
        					break;
        				}
        			}
        		}
			} catch(FileNotFoundException fne) {
		    	 return new ResponseEntity<>("User does not have access to the project " + remoteProjectId, HttpStatus.FORBIDDEN);
			} catch (IOException ioe) {
        		_logger.error("Issue querying user permissions", ioe);
				 return new ResponseEntity<>("User " + username + " probably has Collaborator level access. Xsync will fail.", HttpStatus.FORBIDDEN);
			} catch(Exception e) {
				_logger.error("Issue querying user permissions", e);
				 return new ResponseEntity<>("Could not connect", HttpStatus.BAD_REQUEST);
	        }
        } catch (Exception e) {
			_logger.error("Issue querying user permissions", e);
        	return new ResponseEntity<>("Could not connect", HttpStatus.BAD_REQUEST);
        }
		if (!found) {
			if (permitted) {
	           	return new ResponseEntity<>("User  " + username + " may have All Data Access.", HttpStatus.ACCEPTED);
			} else
				return new ResponseEntity<>("User  " + username + " has no access", HttpStatus.FORBIDDEN);
		} else {
           	return new ResponseEntity<>("User  " + username + " has access.", HttpStatus.ACCEPTED);
		}
	}

	@AuthDelegate(XsyncEditProjectUserAuthority.class)
    @XapiRequestMapping(path="/check/projects/{projectId}", method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_JSON_VALUE, restrictTo = AccessLevel.Authorizer)
    @ApiOperation(value = "Checks whether XSync remote credentials are valid")
    @ApiResponses({@ApiResponse(code = 200, message = "Remote credentials valid."),
			@ApiResponse(code = 500, message = "Unexpected error")})
	public synchronized ResponseEntity<String> checkRemoteCredentials(@PathVariable("projectId") String projectId,
			@RequestBody XsyncRemoteCredentialsPojo credentialsPojo) {
		try {
	        if (StringUtils.isBlank(credentialsPojo.getHost()) || StringUtils.isBlank(credentialsPojo.getLocalProject())) {
	        	return new ResponseEntity<>("Could not check remote credentials.  Incomplete information supplied.", HttpStatus.BAD_REQUEST );
	        }
			RemoteAliasEntity remoteAliasEntity = _remoteAliasService.getRemoteAliasEntity(credentialsPojo.getLocalProject(), credentialsPojo.getHost());

			if (isHostConnectionAllowed(remoteAliasEntity)) {
				try {
					final URL url = new URL (credentialsPojo.getHost() + "/data/JSESSIONID");
					final byte[] encoding = Base64.encodeBase64((remoteAliasEntity.getRemote_alias_token() + ":" + remoteAliasEntity.getRemote_alias_password()).getBytes());
					final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("GET");
					connection.setDoOutput(true);
					connection.setRequestProperty  ("Authorization", "Basic " + new String(encoding, StandardCharsets.UTF_8));
					try (final InputStream content = connection.getInputStream()) {
						final String results = IOUtils.toString(content, StandardCharsets.UTF_8);
						return new ResponseEntity<>(results, HttpStatus.OK);
					}
				} catch (Exception e) {
					return new ResponseEntity<>("Could not connect", HttpStatus.BAD_REQUEST);
				}
			} else {
				return new ResponseEntity<>("Check project configuration", HttpStatus.FORBIDDEN);
			}
		} catch (Exception exception) {
        	return new ResponseEntity<>("Could not connect", HttpStatus.INTERNAL_SERVER_ERROR );
		}
	}

	private boolean isHostConnectionAllowed(final RemoteAliasEntity remoteAliasEntity) {
		return null != remoteAliasEntity;
	}
}
