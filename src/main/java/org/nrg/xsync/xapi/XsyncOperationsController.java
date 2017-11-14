package org.nrg.xsync.xapi;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.net.AuthenticatedClientHttpRequestFactory;
import org.nrg.framework.services.SerializerService;
import org.nrg.framework.utilities.BasicXnatResourceLocator;
import org.nrg.mail.services.MailService;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XsyncXsyncassessordata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xnat.xsync.annotation.IdGenerator;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteOperation;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.exception.XsyncCredentialsRequiredException;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.local.SingleExperimentTransfer;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.remote.alias.services.SyncStatusService;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.StringStreamingResponseBody;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import com.google.gson.JsonObject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api(description = "XNAT XSync Operations API")
@XapiRestController
@RequestMapping(value = "/xsync")
public class XsyncOperationsController extends AbstractXapiProjectRestController {

	private final QueryResultUtil            _queryResultUtil;
    private final ConfigService              _configService;
    private final SerializerService          _serializer;
    private final MailService                _mailService;
    private final CatalogService 	         _catalogService;
    private final XsyncXnatInfo              _xnatInfo;
    private final NamedParameterJdbcTemplate _jdbcTemplate;
    private final SyncStatusService 		 _syncStatusService;
    private List<Resource> _resourceList;

    @Autowired
    public XsyncOperationsController(final RemoteConnectionManager manager,
                                     final UserManagementServiceI userManagementService,
                                     final RoleHolder roleHolder,
                                     final ConfigService configService,
                                     final MailService mailService,
                                     final CatalogService catalogService,
                                     final XsyncXnatInfo xnatInfo, final SerializerService serializer,
                                     final QueryResultUtil queryResultUtil, final JdbcTemplate jdbcTemplate,
                                     final Map<String, HttpMessageConverter<?>> converters,
                                     final ExecutorService executorService,
                                     final SyncStatusService syncStatusService) {
        super(userManagementService, roleHolder);
        _configService = configService;
        _mailService = mailService;
        _catalogService = catalogService;
        _xnatInfo = xnatInfo;
        _serializer = serializer;
        _queryResultUtil = queryResultUtil;
        _jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        _converters = new ArrayList<>(converters.values());
        _syncStatusService = syncStatusService;
        if (!converters.containsKey("stringHttpMessageConverter")) {
            _converters.add(new StringHttpMessageConverter());
        }
        _executorService = executorService;
        _manager = manager;
    }

    @ApiOperation(value = "Exports the indicated project.", notes = "Starts the project export operation for the project with the indicated ID.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The project export operation was successfully started."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to export the indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/projects/{projectId}", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> exportProject(@PathVariable("projectId") final String projectId) throws URISyntaxException, XsyncNotConfiguredException {
    	
    	if(!_syncStatusService.getProjectSyncStatus(projectId).isSyncing())
    	{
	    	//Check user credentials to see if the user is a member or an owner of the project
	        final UserI user = getSessionUser();
	    	try {
	        	final HttpStatus status = canDeleteProject(projectId);
	            if (status != null) {
	                return new ResponseEntity<>(status);
	            }
	        }catch(Exception e) {
	            if (_log.isInfoEnabled()) {
	                _log.info("Unable to fech user permissions for user " + user.getLogin()  + " Project " + projectId );
	            }
	        }
	    	final ProjectChangeDiscoverer projectChange = new ProjectChangeDiscoverer(_manager, _configService, _serializer, _queryResultUtil, _jdbcTemplate, _mailService,_catalogService, _xnatInfo, _syncStatusService, projectId, getSessionUser());
	        _executorService.submit(projectChange);
	        if (_log.isInfoEnabled()) {
	            _log.info("Project " + projectId + " is being exported by " + getSessionUser().getUsername());
	        }
	        return new ResponseEntity<>(projectId + " synchronization started", HttpStatus.OK);
        }
    	else
    		return new ResponseEntity<>("Sync is currently running for this project. &nbsp;Please await the results of that sync or try again later.", HttpStatus.OK);
    }
    
    
    @ApiOperation(value = "Exports the indicated experiment.", notes = "Starts the Experiment export operation as indicated by the ID. WARNING: Will overwrite remote data", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The experiment export operation was successfully started."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to export the indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "/syncexperiment/{experimentId}", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> syncSingleExperiment(@PathVariable("experimentId") final String experimentId) throws URISyntaxException, XsyncNotConfiguredException {
        final UserI user = getSessionUser();
        
    	//Is the experiment ID in the DB
    	XnatExperimentdata exp = XnatExperimentdata.getXnatExperimentdatasById(experimentId, user, false);
    	if (exp == null) {
    		//Incorrect Experiment ID
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	//Get the project id
    	String projectId = exp.getProject(); 
    	//Check user credentials to see if the user is a member or an owner of the project
    	try {
        	final HttpStatus status = canDeleteProject(projectId);
            if (status != null) {
                return new ResponseEntity<>(status);
            }
        }catch(Exception e) {
            if (_log.isInfoEnabled()) {
                _log.info("Unable to fech user permissions for user " + user.getLogin()  + " Project " + projectId );
            }
        }
    	if (_syncStatusService.isCurrentlySyncing(projectId)) {
    		return new ResponseEntity<>(HttpStatus.LOCKED);
    	}
    	
        final List<XsyncXsyncassessordata> okToSyncDatas = XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", experimentId, user, true);
        final XsyncXsyncassessordata okToSyncData;
    	if (okToSyncDatas != null && okToSyncDatas.size() > 0) {
            okToSyncData = okToSyncDatas.get(0);
            if (!okToSyncData.getSyncStatus().equals(XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC)) {
            	okToSyncData.setSyncStatus(XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC);
            	//Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
            	final EventMetaI c = EventUtils.DEFAULT_EVENT(user, "ADMIN_EVENT occurred");
            	boolean saved;
            	try {
            		saved = okToSyncData.save(user, false, true, c);
            		if (!saved) {
            			return new ResponseEntity<>("Unable to marc sync assessor for syncing.", HttpStatus.INTERNAL_SERVER_ERROR);
            		}
            	} catch (Exception e) {
            		return new ResponseEntity<>("Unable to marc sync assessor for syncing.", HttpStatus.INTERNAL_SERVER_ERROR);
            	}
           	}
        } 
    	
    	final SingleExperimentTransfer singleExperimentTransfer = new SingleExperimentTransfer(_manager, _configService, _serializer,
    			_queryResultUtil, _jdbcTemplate, _mailService,_catalogService, _xnatInfo, _syncStatusService, projectId, user, exp.getId());
        _executorService.submit(singleExperimentTransfer);
        if (_log.isInfoEnabled()) {
            _log.info("Experiment[ " + exp.getLabel() + "@" + projectId +"]" + experimentId + " is being exported by " + getSessionUser().getUsername());
        }
        return new ResponseEntity<>(experimentId + " synchronization started", HttpStatus.OK);
    }
    
    @ApiOperation(value = "Sets OK to Sync Status for the experiment.", notes = "Sets OK to Sync Status for the experiment.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The project export operation was successfully started."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to export the indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/experiments/{experimentId}", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> exportExperiment(@PathVariable("experimentId") final String experimentId, @RequestParam("okToSync") final boolean okToSync) throws URISyntaxException, XsyncNotConfiguredException {
        //If the OkToSync Assessor already exists, update that
        //If it does not exist, create one.
        final UserI user = getSessionUser();
    	try {
            final XnatExperimentdata experiment = XnatExperimentdata.getXnatExperimentdatasById(experimentId, user, false);
        	final HttpStatus status = canEditProject(experiment.getProject());
            if (status != null) {
                return new ResponseEntity<>(status);
            }
        }catch(Exception e) {
            if (_log.isInfoEnabled()) {
                _log.info("Unable to fech user permissions for user " + user.getLogin()  + " Experiment " + experimentId );
            }
        }

        final List<XsyncXsyncassessordata> okToSyncDatas = XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", experimentId, user, true);

        final XsyncXsyncassessordata okToSyncData;

        try {
            final XnatExperimentdata experiment = XnatExperimentdata.getXnatExperimentdatasById(experimentId, user, false);
        	final XsyncXsyncprojectdata syncProjectConfiguration = (new XsyncUtils(_serializer, _jdbcTemplate, user)).getSyncDetailsForProject(experiment.getProject());
        	boolean alreadySynced=false;
        	boolean wasSkipped=false;
        	if (okToSyncDatas != null && okToSyncDatas.size() > 0) {
                okToSyncData = okToSyncDatas.get(0);
                final String previousSyncStatus = okToSyncData.getSyncStatus();
                if (previousSyncStatus.equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_NOT_VERIFIED) || 
                    previousSyncStatus.equals(XsyncUtils.SYNC_STATUS_SYNCED_AND_VERIFIED)) {
                	alreadySynced=true;
                } else if (previousSyncStatus.equals(XsyncUtils.SYNC_STATUS_SKIPPED)) {
                	wasSkipped=true;
                }
                if (okToSyncData.getOktosync() != okToSync) {
                    okToSyncData.setOktosync(okToSync);
                    if (!alreadySynced && !wasSkipped) {
                    	okToSyncData.setSyncStatus(XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC);
                    }
                    okToSyncData.setRemoteUrl(syncProjectConfiguration.getSyncinfo().getRemoteUrl());
                    okToSyncData.setRemoteProjectId(syncProjectConfiguration.getSyncinfo().getRemoteProjectId());
                }
            } else {
                okToSyncData = createNewXsyncassessor(experimentId, okToSync, user);
            }
            if (okToSyncData != null) {
                //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
                final EventMetaI c = EventUtils.DEFAULT_EVENT(user, "ADMIN_EVENT occurred");
                final boolean saved = okToSyncData.save(user, false, true, c);
                if (!saved) {
                    return new ResponseEntity<>("Unable to save the Ok To Sync Information", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            StringBuilder responseText = new StringBuilder("<p>" + experimentId + " has been marked " + (okToSync ? "OK" : "Not OK") + " to sync");
            if (!alreadySynced && !wasSkipped) {
            	responseText.append(".  This experiment will ").append((okToSync) ? "" : "not ").append("be synced in the next synchronization cycle of the project ").append(experiment.getProject()).append(".");
            } else if (alreadySynced) {
            	responseText.append(", however <em>this session has already been synced</em>.  ");
            	responseText.append((okToSync) ? "It will not be scheduled to be resynced.</p><p>Please use the <em>Sync This Session Now</em> " + 
            			"button if you wish to manually resync this session or the <em>Mark Session For Sync</em> button if you wish to schedule this " +
            			"session to be synced with the next project sync.</p>" :
            			"</p><p>Please check the destination to view the current status of the session if you feel it should not have been sent.</p>");
            } else {
            	responseText.append(", however records indicate that this session was skipped by another sync process.  This most often happens " +
            			"if the session was manually uploaded to the destination site, but it can occur under other circumstances.</p>");
            	responseText.append((okToSync) ? "<p>Please check the destination site and use the <em>Sync This Session Now</em> " + 
            			"button if you wish to manually sync this session or the <em>Mark Session For Sync</em> button if you wish to schedule this " 
            					+ "session to be synced with the next project sync.</p>" :
            			"<p>Please check the destination to view the current status of the session if you feel it should not have been sent.</p>");
            }
            return new ResponseEntity<>(responseText.toString(), HttpStatus.OK);
        } catch (Exception e) {
            final String message = "An error occurred trying to export the experiment " + experimentId;
            _log.error(message, e);
            return new ResponseEntity<>(message + ": " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    

    /**
     * Gets the sync info.
     *
     * @param experimentId the experiment id
     * @return the sync info
     */
    @XapiRequestMapping(value = "/experiments/{experimentId}/syncStatus", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> getSyncInfo(@PathVariable("experimentId") final String experimentId){
        final UserI user = getSessionUser();
    	try {
            final XnatExperimentdata experiment = XnatExperimentdata.getXnatExperimentdatasById(experimentId, user, false);
        	final HttpStatus status = canEditProject(experiment.getProject());
            if (status != null) {
                return new ResponseEntity<>(status);
            }
        }catch(Exception e) {
            if (_log.isInfoEnabled()) {
                _log.info("Unable to fetch user permissions for user " + user.getLogin()  + " Experiment " + experimentId );
            }
        }
        final List<XsyncXsyncassessordata> okToSyncDatas = XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", experimentId, user, true);
        XsyncXsyncassessordata okToSyncData = null;
        String resp="";
        try {
        	if (okToSyncDatas != null && okToSyncDatas.size() > 0) {
                okToSyncData = okToSyncDatas.get(0);
            }
            if (okToSyncData != null) {
                	JsonObject syncStatus=new JsonObject();
                	syncStatus.addProperty("syncStatus", okToSyncData.getSyncStatus());
                	syncStatus.addProperty("authorizedBy", okToSyncData.getAuthorizedBy());
                	syncStatus.addProperty("authorizedDate", new SimpleDateFormat("yyyy-MM-dd").format(okToSyncData.getAuthorizedTime()));
                	resp=syncStatus.toString();
            }
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            final String message = "An error occurred while fetching sync status information " + experimentId;
            _log.error(message, e);
            return new ResponseEntity<>(message + ": " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    
    /**
     * Gets Xsync custom id generator classes.
     *
     * @param experimentId the experiment id
     * @return the sync info
     */
    @ApiOperation(value = "Xsync custom Id generators.", notes = "Xsync custom Id generators.")
    @XapiRequestMapping(value = "/getXsyncCustomIdGenerators", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Map<String, String>> getCustomIdentifers(){
    	final Map<String,String> componentList = new TreeMap<String,String>();
    	try {
    		for (final Resource resource : getResourceList()) {
    			final Properties properties = PropertiesLoaderUtils.loadProperties(resource);
    			if (!properties.containsKey(IdGenerator.ID_GENERATOR)) {
    				continue;
    			}
    			String qualifiedClassName=properties.getProperty(IdGenerator.ID_GENERATOR);
    			String className=qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".")+1);
    			componentList.put(className,qualifiedClassName);
    		}
            return new ResponseEntity<>(componentList, HttpStatus.OK);
        } catch (Exception e) {
            _log.error("Error while fetching Xsync Identifier generator classes.", e);
            return new ResponseEntity<>(componentList, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Gets the mapping file.
     *
     * @param experimentId the experiment id
     * @return the sync info
     * @throws Exception 
     */
    @ApiOperation(value = "Xsync subject/experiment id report.", notes = "Xsync subject/experiment id report.")
    @XapiRequestMapping(value = "/getSubjectMappingFile/{projectId}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<StringStreamingResponseBody> getSubjectMappingFile(@PathVariable("projectId") final String projectId,@RequestParam Map<String,String> params) throws Exception{

    		
    		final ProjectChangeDiscoverer projectChangeDiscoverer = new ProjectChangeDiscoverer(_manager, _configService, _serializer, _queryResultUtil, _jdbcTemplate, _mailService,_catalogService, _xnatInfo, _syncStatusService, projectId, getSessionUser());
    		final String reportFormat = params.get(XsyncUtils.REPORT_FORMAT);
    		final String objectType = params.get(XsyncUtils.OBJECT_TYPE);
    	    ResponseEntity<String> stringResponse = new ResponseEntity<String>(projectChangeDiscoverer.generateMappingReport(reportFormat,objectType), HttpStatus.OK);
    		if (!stringResponse.getStatusCode().equals(HttpStatus.OK)) {			
    			return ResponseEntity.status(stringResponse.getStatusCode()).header(HttpHeaders.CONTENT_TYPE, StringStreamingResponseBody.MEDIA_TYPE_TEXT_PLAIN)
    					.body(new StringStreamingResponseBody(stringResponse.getBody()));
    		}
    		else if (params.containsKey(XsyncUtils.REPORT_FORMAT) && params.get(XsyncUtils.REPORT_FORMAT).equalsIgnoreCase(XsyncUtils.REPORT_FORMAT_CSV)) {
    			return ResponseEntity.status(stringResponse.getStatusCode()).header(HttpHeaders.CONTENT_TYPE, StringStreamingResponseBody.MEDIA_TYPE_TEXT_CSV)
    					.header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=" + projectId +"_"+objectType+"s_id_report.csv")
    					.body(new StringStreamingResponseBody(stringResponse.getBody()));
    		} else {
    			return ResponseEntity.status(stringResponse.getStatusCode()).header(HttpHeaders.CONTENT_TYPE, StringStreamingResponseBody.MEDIA_TYPE_APPLICATION_JSON)
    					.body(new StringStreamingResponseBody(stringResponse.getBody()));
    		}
    }
    
    
    private List<Resource> getResourceList() throws IOException {
		return (_resourceList!=null) ? _resourceList : BasicXnatResourceLocator.getResources("classpath*:META-INF/xnat/xsync/*-xsync.properties");
	}
    
    @ApiOperation(value = "Mark session for sync.", notes = "Marks experiment to be synced.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The project export operation was successfully started."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to export the indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "/requestSync/{experimentId}", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> markForSync(@PathVariable("experimentId") final String experimentId) throws URISyntaxException, XsyncNotConfiguredException {
        //If the OkToSync Assessor already exists, update that
        //If it does not exist, create one.
        final UserI user = getSessionUser();
    	try {
            final XnatExperimentdata experiment = XnatExperimentdata.getXnatExperimentdatasById(experimentId, user, false);
        	final HttpStatus status = canEditProject(experiment.getProject());
            if (status != null) {
                return new ResponseEntity<>(status);
            }
        }catch(Exception e) {
            if (_log.isInfoEnabled()) {
                _log.info("Unable to fech user permissions for user " + user.getLogin()  + " Experiment " + experimentId );
            }
        }

        final List<XsyncXsyncassessordata> syncAssessors = XsyncXsyncassessordata.getXsyncXsyncassessordatasByField("xsync:xsyncAssessorData/synced_experiment_id", experimentId, user, true);

        final XsyncXsyncassessordata syncAssessor;

        try {
            final XnatExperimentdata experiment = XnatExperimentdata.getXnatExperimentdatasById(experimentId, user, false);
        	final XsyncXsyncprojectdata syncProjectConfiguration = (new XsyncUtils(_serializer, _jdbcTemplate, user)).getSyncDetailsForProject(experiment.getProject());
        	if (syncAssessors != null && syncAssessors.size() > 0) {
                syncAssessor = syncAssessors.get(0);
                syncAssessor.setSyncStatus(XsyncUtils.SYNC_STATUS_SYNC_REQUESTED);
                syncAssessor.setRemoteUrl(syncProjectConfiguration.getSyncinfo().getRemoteUrl());
                syncAssessor.setRemoteProjectId(syncProjectConfiguration.getSyncinfo().getRemoteProjectId());
            } else {
                syncAssessor = createNewXsyncassessor(experimentId, true, user);
            }
            if (syncAssessor != null) {
                //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
                final EventMetaI c = EventUtils.DEFAULT_EVENT(user, "ADMIN_EVENT occurred");
                final boolean saved = syncAssessor.save(user, false, true, c);
                if (!saved) {
                    return new ResponseEntity<>("Unable to save the sync assessor Information", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            return new ResponseEntity<>(experimentId + " has been marked to sync.", HttpStatus.OK);
        } catch (Exception e) {
            final String message = "An error occurred trying to export the experiment " + experimentId;
            _log.error(message, e);
            return new ResponseEntity<>(message + ": " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @ApiOperation(value = "Makes a REST call to a remote server.", notes = "This call is stateless and doesn't preserve authentication information or any other request information.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The return value from the REST call."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "Not authorized to run REST calls on this server."), @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "remoteREST", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> connectToRemoteRestEndpoint(@RequestBody final RemoteOperation operation) throws URISyntaxException, XsyncCredentialsRequiredException {
        //HttpStatus status = isPermitted();
        //if (status != null) {
        //    return new ResponseEntity<>(status);
        //}

        final String username = operation.getUsername();
        final String password = operation.getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            throw new XsyncCredentialsRequiredException(getSessionUser().getUsername(), operation.getUrl(), StringUtils.isBlank(username), StringUtils.isBlank(password));
        }

        final RestTemplate template = getRestTemplate(operation);
        final HttpMethod method = HttpMethod.resolve(operation.getMethod());
        if (_log.isDebugEnabled()) {
            _log.debug("Attempting to " + method + " to URL " + operation.getUrl() + " as user " + username);
        }
        final String value;
        switch (method) {
            case GET:
                value = template.getForObject(operation.getUrl(), String.class);
                break;
            case PUT:
                final ResponseEntity<String> response = template.exchange(operation.getUrl(), HttpMethod.PUT, HttpEntity.EMPTY, String.class);
                value = response.getBody();
                break;
            case POST:
                value = template.postForObject(operation.getUrl(), HttpEntity.EMPTY, String.class);
                break;
            case DELETE:
                template.delete(operation.getUrl());
                value = "";
                break;
            default:
                throw new UnsupportedOperationException("The HTTP method " + operation.getMethod() + " is not supported by this API.");
        }

        return new ResponseEntity<>(value, HttpStatus.OK);
    }

    @ApiOperation(value = "Gets the log file to display the progress of sync.", notes = "Gets the log file containing sync progress")
    @XapiRequestMapping(value = "/progress/{projectId}",  method = RequestMethod.GET)
    @ResponseBody
    public void getSyncProgress(HttpServletRequest request, HttpServletResponse response, @PathVariable("projectId") final String projectId) throws URISyntaxException, XsyncCredentialsRequiredException {
        final UserI user = getSessionUser();
    	try {
        	final HttpStatus status = canReadProject(projectId);
            if (status != null) {
                return ;
            }
        }catch(Exception e) {
            if (_log.isInfoEnabled()) {
                _log.info("Unable to fech user permissions for user " + user.getLogin()  + " Project " + projectId );
            }
        }

    	String syncStatusFilePath = SynchronizationManager.GET_SYNC_LOG_FILE_PATH(projectId);
        Path file = Paths.get(syncStatusFilePath);
        if (Files.exists(file)) 
        {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.addHeader("Content-Disposition", "attachment; filename="+file.getFileName());
            try
            {
                Files.copy(file, response.getOutputStream());
                response.getOutputStream().flush();
            } 
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private XsyncXsyncassessordata createNewXsyncassessor(final String experimentId, final boolean okToSync, final UserI user) throws Exception {
        final XsyncXsyncassessordata okToSyncData;
        final XnatExperimentdata experiment = XnatExperimentdata.getXnatExperimentdatasById(experimentId, user, false);
        final XsyncXsyncprojectdata syncProjectConfiguration = (new XsyncUtils(_serializer, _jdbcTemplate, user)).getSyncDetailsForProject(experiment.getProject());

        //Create a new one
        okToSyncData = new XsyncXsyncassessordata();
        okToSyncData.setId(XsyncXsyncassessordata.CreateNewID());
        okToSyncData.setLabel(experiment.getLabel() + "_XSYNC_INFO");
        okToSyncData.setProject(experiment.getProject());
        if (okToSyncData.getDate() == null) {
            okToSyncData.setDate(new Date()); //Setting first time
        }
        okToSyncData.setAuthorizedBy(user.getLogin());
        okToSyncData.setAuthorizedTime(new Date());
        okToSyncData.setSyncStatus(XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC);

        okToSyncData.setRemoteUrl(syncProjectConfiguration.getSyncinfo().getRemoteUrl());
        okToSyncData.setRemoteProjectId(syncProjectConfiguration.getSyncinfo().getRemoteProjectId());
        if (experiment instanceof XnatSubjectassessordata) {
            okToSyncData.setSubjectId(((XnatSubjectassessordata) experiment).getSubjectId());
        } else {
            throw new Exception("Expecting a subject assessor to set synchronization");
        }
        okToSyncData.setSyncedExperimentId(experimentId);
        okToSyncData.setOktosync(okToSync);
        return okToSyncData;
    }

    private RestTemplate getRestTemplate(final RemoteOperation operation) throws URISyntaxException {
        AuthenticatedClientHttpRequestFactory factory = new AuthenticatedClientHttpRequestFactory(operation.getUsername(), operation.getPassword());
        if (StringUtils.isNotBlank(operation.getProxy())) {
            factory.setProxy(new URI(operation.getProxy()));
        }
        final RestTemplate template = new RestTemplate(factory);
        template.setMessageConverters(_converters);
        return template;
    }

    private static final Logger _log = LoggerFactory.getLogger(XsyncOperationsController.class);
    private final List<HttpMessageConverter<?>> _converters;
    private final ExecutorService               _executorService;
    private final RemoteConnectionManager       _manager;
}
