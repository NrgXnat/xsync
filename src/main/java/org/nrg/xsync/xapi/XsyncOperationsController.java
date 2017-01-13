package org.nrg.xsync.xapi;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.net.AuthenticatedClientHttpRequestFactory;
import org.nrg.framework.services.SerializerService;
import org.nrg.mail.services.MailService;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
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
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteOperation;
import org.nrg.xsync.discoverer.ProjectChangeDiscoverer;
import org.nrg.xsync.exception.XsyncCredentialsRequiredException;
import org.nrg.xsync.exception.XsyncNotConfiguredException;
import org.nrg.xsync.manager.SynchronizationManager;
import org.nrg.xsync.tools.XsyncXnatInfo;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
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
                                     final ExecutorService executorService) {
        super(userManagementService, roleHolder);
        _configService = configService;
        _mailService = mailService;
        _catalogService = catalogService;
        _xnatInfo = xnatInfo;
        _serializer = serializer;
        _queryResultUtil = queryResultUtil;
        _jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        _converters = new ArrayList<>(converters.values());
        if (!converters.containsKey("stringHttpMessageConverter")) {
            _converters.add(new StringHttpMessageConverter());
        }
        _executorService = executorService;
        _manager = manager;
    }

    @ApiOperation(value = "Exports the indicated project.", notes = "Starts the project export operation for the project with the indicated ID.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The project export operation was successfully started."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to export the indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "/projects/{projectId}", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> exportProject(@PathVariable("projectId") final String projectId) throws URISyntaxException, XsyncNotConfiguredException {
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
    	final ProjectChangeDiscoverer projectChange = new ProjectChangeDiscoverer(_manager, _configService, _serializer, _queryResultUtil, _jdbcTemplate, _mailService,_catalogService, _xnatInfo, projectId, getSessionUser());
        _executorService.submit(projectChange);
        if (_log.isInfoEnabled()) {
            _log.info("Project " + projectId + " is being exported by " + getSessionUser().getUsername());
        }
        return new ResponseEntity<>(projectId + " synchronization started", HttpStatus.OK);
    }
    
    

    @ApiOperation(value = "Clears the Sync Blocked Status", notes = "Clears the projects block status")
    @ApiResponses({@ApiResponse(code = 200, message = "The project was unblocked"), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to  unblock the indicated project."), @ApiResponse(code = 404, message = "Project not configured to sync yet."),@ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "/unblock/{projectId}",  method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> unblock(@PathVariable("projectId") final String projectId) throws URISyntaxException, XsyncNotConfiguredException {
        final UserI user = getSessionUser();
    	try {
        	final HttpStatus status = canEditProject(projectId);
            if (status != null) {
                return new ResponseEntity<>(status);
            }
        }catch(Exception e) {
            if (_log.isInfoEnabled()) {
                _log.info("Unable to fech user permissions for user " + user.getLogin()  + " Project " + projectId );
            }
        }

        final List<XsyncXsyncprojectdata> syncProjectDatas = XsyncXsyncprojectdata.getXsyncXsyncprojectdatasByField("xsync:xsyncProjectData/source_project_id",projectId, user, true);
        final XsyncXsyncprojectdata syncProjectData;

        try {
            if (syncProjectDatas != null && syncProjectDatas.size() > 0) {
            	syncProjectData = syncProjectDatas.get(0);
                if (syncProjectData != null) {
                	if (syncProjectData.getSyncBlocked()) {
	                		syncProjectData.setSyncBlocked(new Boolean(false));
	                    //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
	                    EventMetaI c = EventUtils.DEFAULT_EVENT(user, "ADMIN_EVENT occurred");
	                    boolean saved = syncProjectData.save(user, false, true, c);
	                    if (!saved) {
	                        return new ResponseEntity<>("Unable to save Project Sync Block Information", HttpStatus.INTERNAL_SERVER_ERROR);
	                    }
                	}else {
                        return new ResponseEntity<>(projectId + " sync block was already unblocked ", HttpStatus.OK);
                	}
                }
                return new ResponseEntity<>(projectId + " sync block has been unblock ", HttpStatus.OK);
            }else 
                return new ResponseEntity<>("Unable to find Project Sync Information", HttpStatus.NOT_FOUND);
        }catch (Exception e) {
            final String message = "An error occurred trying to unblock the project " + projectId;
            _log.error(message, e);
            return new ResponseEntity<>(message + ": " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    @ApiOperation(value = "Sets OK to Sync Status for the experiment.", notes = "Sets OK to Sync Status for the experiment.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The project export operation was successfully started."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "User not authorized to export the indicated project."), @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "/experiments/{experimentId}", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
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
        	if (okToSyncDatas != null && okToSyncDatas.size() > 0) {
                okToSyncData = okToSyncDatas.get(0);
                if (okToSyncData.getOktosync() != okToSync) {
                    okToSyncData.setOktosync(okToSync);
                    okToSyncData.setSyncStatus(XsyncUtils.SYNC_STATUS_WAITING_TO_SYNC);
                    okToSyncData.setRemoteUrl(syncProjectConfiguration.getSyncinfo().getRemoteUrl());
                    okToSyncData.setRemoteProjectId(syncProjectConfiguration.getSyncinfo().getRemoteProjectId());
                }
            } else {
                okToSyncData = createNewXsyncassessor(experimentId, okToSync, user);
            }
            if (okToSyncData != null) {
                //Backward compatible XNAT 1.6.5 does not have ADMIN_EVENT method
                EventMetaI c = EventUtils.DEFAULT_EVENT(user, "ADMIN_EVENT occurred");
                boolean saved = okToSyncData.save(user, false, true, c);
                if (!saved) {
                    return new ResponseEntity<>("Unable to save the Ok To Sync Information", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            return new ResponseEntity<>(experimentId + " has been marked " + (okToSync ? "OK" : "Not OK") + " to sync", HttpStatus.OK);
        } catch (Exception e) {
            final String message = "An error occurred trying to export the experiment " + experimentId;
            _log.error(message, e);
            return new ResponseEntity<>(message + ": " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @ApiOperation(value = "Makes a REST call to a remote server.", notes = "This call is stateless and doesn't preserve authentication information or any other request information.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The return value from the REST call."), @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."), @ApiResponse(code = 403, message = "Not authorized to run REST calls on this server."), @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = "remoteREST", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE, method = RequestMethod.POST)
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
    @RequestMapping(value = "/progress/{projectId}",  method = RequestMethod.GET)
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

    private static final Logger _log = Logger.getLogger(XsyncOperationsController.class);
    private final List<HttpMessageConverter<?>> _converters;
    private final ExecutorService               _executorService;
    private final RemoteConnectionManager       _manager;
}
