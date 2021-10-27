package org.nrg.xsync.services.remote;


import org.apache.commons.lang3.exception.ExceptionUtils;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xsync.configuration.XsyncSitePreferencesBean;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.manager.SynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;


/**
 * The Class RemoteRESTServiceImpl.
 */
@Service
//@EnableRetry
//TODO update to retry when we upgrade spring to 4
public class RemoteRESTServiceImpl  extends AbstractRemoteRESTService implements RemoteRESTService {

	/** The logger. */
	public static Logger logger = LoggerFactory.getLogger(RemoteRESTServiceImpl.class);
	// TODO:  Do we want this to be configurable?
	public static final int TRUNCATE_LOG_OUTPUT_LENGTH = 1000;

	private final XsyncSitePreferencesBean _prefs;
	private long sleep = 10;
	private int maxTries = 1;


	@Autowired
	public RemoteRESTServiceImpl(final XsyncSitePreferencesBean prefs) {
		_prefs = prefs;
	}

	@PostConstruct
	private void getXsyncPreferences() {
		maxTries = _prefs.getSyncRetryCountInt();
		//sleep = _prefs.getSyncRetryIntervalInMillis() * 1000;
		sleep = _prefs.getSyncRetryIntervalInMillis();
	}
	
	public RemoteConnectionResponse importXar(final RemoteConnection connection,  final String xarPath) throws RuntimeException{
		//this.setAliasToken(connection);
		final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		final File tempFile;
		// The uploader expects a file, so we'll give it an empty one
		try {
			tempFile = File.createTempFile("temp", "xarupload");
		} catch (IOException e1) {
			throw new RuntimeException("ERROR: Couldn't create temp file");
		}
		body.add("field", "value");
		body.add("import-handler","XAR");
		body.add("file", new FileSystemResource(tempFile));
		ResponseEntity<String> response;
		try {
			try {
				final HttpHeaders header = RemoteConnectionManager.GetAuthHeaders(connection, true);
				//header.setContentLength(xar.length());
				//header.setContentType(MediaType.TEXT_PLAIN);
				//header.setContentLength(1);
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import?import-handler=XAR&localFilePath="
						+ xarPath + "&removeLocalFileAfterImport=true", HttpMethod.POST, httpEntity, String.class);
			} catch (XsyncHttpAuthenticationException authex) {
				final HttpHeaders header = RemoteConnectionManager.GetAuthHeaders(connection, false, true);
				//header.setContentType(MediaType.TEXT_PLAIN);
				//header.setContentLength(1);
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import?import-handler=XAR&localFilePath=" 
						+ xarPath + "&removeLocalFileAfterImport=true", HttpMethod.POST, httpEntity, String.class);
			}
			logger.info(truncateStr(response));
			logger.info(truncateStr(response.getBody()));
			logger.info(truncateStr(response.getHeaders().get("Set-Cookie")));
			// Tests for Bad Request and Internal Server Error in addition to OK and Created.  Those error statues will
			// be thrown by invalid XAR requests, and we don't want a long wait with retry for errors returned by the
			// XarImporter class.
			tempFile.delete();
			final HttpStatus statusCode = response.getStatusCode();
			final boolean    status     = COMPLETED_STATUSES.contains(statusCode) || ERROR_STATUSES.contains(statusCode);
			if(!status){
				throw new RuntimeException("importXar request failed. Retrying...");
			}else{
				return new RemoteConnectionResponse(response);
			}
		} catch (RuntimeException e) {
			if (e instanceof NestedRuntimeException) {
				final Throwable specCause = ((NestedRuntimeException)e).getMostSpecificCause(); 
				// Let's not keep trying these error types either.  They will be thrown by invalid XAR requests, and we don't want a
				// long wait with retry for errors returned by the XarImporter class.
				if (specCause instanceof HttpServerErrorException) {
					return new RemoteConnectionResponse(new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR));
				}
			}
			throw(e);
		}
	}

	/**
	 * importXar with retry.
	 *
	 * @param connection the connection
	 * @param xar the xar
	 * @return true, if successful
	 * @throws RuntimeException the runtime exception
	 */
	public RemoteConnectionResponse importXar(RemoteConnection connection,  File xar) throws RuntimeException{
		int count = 0;
		while(true) {
		    try {
		    	 logger.debug("Attempting xar import:  File=" + xar.getName());
		         return this.importXarWithoutRetry(connection, xar);
		    } catch (RuntimeException e) {
		    	count++;
	    		logger.error("Exception thrown during storeXAR process:\n" + ExceptionUtils.getStackTrace(e));
	    		logger.debug((maxTries>0 && maxTries>=count) ? "StoreXAR failed.  Maximum attemts has not yet been reached.  Upload will be reattempted in " + String.valueOf(sleep/1000) + " seconds." : 
	    				"Maximum attemts has been reached.  StoreXAR will not be retried.");
		        if (maxTries == 0 || count > maxTries) throw e;
		    	try {
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
	    		logger.debug("Retrying importXar: retrycount "+ count + " out of " + maxTries);
		    }
		}
	}
	
	/**
	 * Import xar without retry.
	 *
	 * @param connection the connection
	 * @param xar the xar
	 * @return true, if successful
	 * @throws RuntimeException the runtime exception
	 */
	//@Retryable(maxAttempts=5,value=RuntimeException.class,backoff= @Backoff(delay=100, maxDelay=500))
	//TODO update to retry when we upgrade spring to 4
	private RemoteConnectionResponse importXarWithoutRetry(RemoteConnection connection,  File xar) throws RuntimeException{
		//this.setAliasToken(connection);
		final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("field", "value");
		body.add("import-handler","XAR");
		body.add("file", new FileSystemResource(xar));
		
		ResponseEntity<String> response;
		try {
			try {
				HttpHeaders header = RemoteConnectionManager.GetAuthHeaders(connection, true);
				//header.setContentLength(xar.length());
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				logger.debug("Sending XAR to destination");
				long starttime= System.currentTimeMillis();
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import", HttpMethod.POST, httpEntity, String.class);
				long endtime=System.currentTimeMillis();
				long totaltime=endtime-starttime;
				logger.debug("Total Time to process XAR file:"+ totaltime +" ms.");
			} catch (XsyncHttpAuthenticationException authex) {
				HttpHeaders header = RemoteConnectionManager.GetAuthHeaders(connection, false, true);
				//header.setContentLength(xar.length());
				logger.debug("Retrying after getting Authentication headers");
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import", HttpMethod.POST, httpEntity, String.class);
			}
			logger.info("importXar"+xar.getAbsolutePath());
			logger.info("POST file length: " + xar.length());
			logger.info(truncateStr(response));
			logger.info(truncateStr(response.getBody()));
			logger.info(truncateStr(response.getHeaders().get("Set-Cookie")));
			// Tests for Bad Request and Internal Server Error in addition to OK and Created.  Those error statues will
			// be thrown by invalid XAR requests, and we don't want a long wait with retry for errors returned by the
			// XarImporter class.
			final HttpStatus statusCode = response.getStatusCode();
			final boolean    status     = COMPLETED_STATUSES.contains(statusCode) || ERROR_STATUSES.contains(statusCode);
			if(!status){
				throw new RuntimeException("importXar request failed. Retrying...");
			}else{
				return new RemoteConnectionResponse(response);
			}
		} catch (RuntimeException e) {
			logger.error("importXar process failed for "+xar.getAbsolutePath());
			logger.error(e.getMessage());
			logger.error(ExceptionUtils.getStackTrace(e));
			//Add error message here for logs.
			if (e instanceof NestedRuntimeException) {
				final Throwable specCause = ((NestedRuntimeException)e).getMostSpecificCause(); 
				// Let's not keep trying these error types either.  They will be thrown by invalid XAR requests, and we don't want a
				// long wait with retry for errors returned by the XarImporter class.
				if (specCause instanceof HttpServerErrorException) {
					return new RemoteConnectionResponse(new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR));
				}
			}
			throw(e);
		}
	}
	
	/**
	 * Import Zip without retry.
	 *
	 * @param connection the connection
	 * @param zip the zip
	 * @return true, if successful
	 * @throws RuntimeException the runtime exception
	 */
	//@Retryable(maxAttempts=5,value=RuntimeException.class,backoff= @Backoff(delay=100, maxDelay=500))
	//TODO update to retry when we upgrade spring to 4
	private RemoteConnectionResponse importZipWithoutRetry(RemoteConnection connection,  String uri, File zip) throws RuntimeException{
		//this.setAliasToken(connection);
		
		final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("field", "value");
		if (zip != null) body.add("file", new FileSystemResource(zip));
		
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(uri, HttpMethod.PUT, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException authex) {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
			response = getResttemplate().exchange(uri, HttpMethod.PUT, httpEntity, String.class);
		}
		logger.info(truncateStr(response));
		logger.info(truncateStr(response.getBody()));
		logger.info(truncateStr(response.getHeaders().get("Set-Cookie")));
		final boolean status = COMPLETED_STATUSES.contains(response.getStatusCode());
		if (zip != null) {
			logger.warn("importZip"+zip.getName());
		}
		if(!status){
			throw new RuntimeException("importZip request failed. Retrying...");
		}else{
			return new RemoteConnectionResponse(response);
		}
	}


	/**
	 * Create Workflow with retry.
	 *
	 * @param connection the connection
	 * @return response
	 */
	public RemoteConnectionResponse createWorkflow(RemoteConnection connection,WrkWorkflowdata wrk ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		         return this.createWorkflowWithoutRetry( connection, wrk );
		    } catch (RuntimeException e) {
		    	try {
		    		logger.debug("Exception " + e.getMessage());
			    	if (maxTries > 0) {
				    	logger.debug("createWorkflow: retrycount "+ count);
				    	logger.debug("Refresh rate is " + _prefs.getSyncRetryCountInt());
				    	logger.debug("Refresh rate is " + _prefs.getSyncRetryInterval());
				    	logger.debug("Sleeping for " + sleep + " milliseconds");
			    		Thread.sleep(sleep);
			    	}
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * Create Workflow without retry.
	 *
	 * @param connection the connection
	 * @param wrk the Workflow
	 * @return true, if successful
	 */
	//TODO @Retryable(maxAttempts=5) update to retry when we upgrade spring to 4
	private RemoteConnectionResponse createWorkflowWithoutRetry(RemoteConnection connection,WrkWorkflowdata wrk ) throws Exception{
		//do we need the assessor data and how.
		//MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();     
		final String wrkXml=wrk.getItem().toXML_String();
		
		ResponseEntity<String> response;
		try {
			logger.debug("URL: " + connection.getUrl()+"/data/workflows?req_format=xml");
			final HttpEntity<?> httpEntity = new HttpEntity<>(wrkXml, RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(connection.getUrl()+"/data/workflows?req_format=xml", HttpMethod.PUT, httpEntity, String.class);
			logger.debug(response.toString());
		} catch (XsyncHttpAuthenticationException authex) {
			try {
				final HttpEntity<?> httpEntity = new HttpEntity<>(wrkXml, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
				response = getResttemplate().exchange(connection.getUrl()+"/data/workflows?req_format=xml", HttpMethod.PUT, httpEntity, String.class);
				logger.debug(response.toString());
			}catch(Exception e) {
				logger.debug("Error while storing workflow " + e.getMessage());
				String cachePath = SynchronizationManager.GET_SYNC_FILE_PATH(wrk.getExternalid());
				File wrkF = new File(cachePath + "failed_" + wrk.getId()+".xml");
				if (!wrkF.getParentFile().exists())
					wrkF.getParentFile().mkdirs();
				FileWriter fw = new FileWriter(wrkF);
				wrk.toXML(fw, false);
				fw.close();
				throw e;
			}
		}
		
		logger.debug(response.toString());
		//return 	((response.getStatusCode().value()==HttpStatus.OK.value()) || (response.getStatusCode().value()==HttpStatus.CREATED.value()))?true:false;
		return new RemoteConnectionResponse(response);
	}

	
	
	/**
	 * Import Subject with retry.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @return response
	 */
	public RemoteConnectionResponse importSubject(RemoteConnection connection,XnatSubjectdata subject ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		         return this.importSubjectWithoutRetry( connection, subject );
		    } catch (RuntimeException e) {
		    	try {
		    		e.printStackTrace();
		    		logger.debug("Exception " + e.getMessage());
			    	if (maxTries > 0) {
				    	logger.debug("importSubject: retrycount "+ count);
				    	logger.debug("Refresh rate is " + _prefs.getSyncRetryCountInt());
				    	logger.debug("Refresh rate is " + _prefs.getSyncRetryInterval());
				    	logger.debug("Sleeping for " + sleep + " milliseconds");
			    		Thread.sleep(sleep);
			    	}
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * delete Subject without retry.
	 *
	 * @param connection the connection
	 * @param subject  the subject
	 * @return response
	 */
	public RemoteConnectionResponse deleteSubject(RemoteConnection connection,XnatSubjectdata subject ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"?removeFiles=true";
		         return this.deleteWithoutRetry( connection,uri);
		    } catch (RuntimeException e) {
		    	try {
			    	logger.debug("deleteSubject: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * delete Experiment without retry.
	 *
	 * @param connection the connection
	 * @param experiment The experiment
	 * @return response
	 *  
	 */
	public RemoteConnectionResponse deleteExperiment(RemoteConnection connection,XnatExperimentdata experiment ) throws Exception {
		int count = 0;
		while(true) {
		    try {
		    	String subjectId = null;
			    try {
			    	subjectId = (String)experiment.getItem().getProperty("subject_ID");
			    }catch(Exception e1) {
			    	logger.error("Could not find a subject id " + experiment.getLabel(),e1);
			    }
			    if (subjectId != null) { 
			    	String uri = connection.getUrl()+"/data/archive/projects/"+experiment.getProject()+"/subjects/"+subjectId+"/experiments/"+experiment.getId()+"?removeFiles=true";
			    	return this.deleteWithoutRetry( connection,uri);
			    }
		    } catch (Exception e) {
		    	try {
			    	logger.debug("deleteSubject: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * delete Subject Resource with retry.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @return response
	 */
	public RemoteConnectionResponse deleteSubjectResource(RemoteConnection connection,XnatSubjectdata subject, String resourceLabel ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"/resources/"+ resourceLabel +"?removeFiles=true";
		         return this.deleteWithoutRetry( connection, uri);
		    } catch (RuntimeException e) {
		    	try {
			    	logger.debug("deleteSubject: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * delete Project Resource with retry.
	 *
	 * @param connection the connection
	 * @param projectId the Project Accession ID
	 * @return response
	 */
	public RemoteConnectionResponse deleteProjectResource(RemoteConnection connection,String projectId, String resourceLabel ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+projectId+"/resources/"+ resourceLabel +"?removeFiles=true";
		         return this.deleteWithoutRetry( connection, uri);
		    } catch (RuntimeException e) {
		    	try {
			    	logger.debug("deleteProjectResource: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	
	/**
	 * import Subject Resource with retry.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @return response
	 */
	public RemoteConnectionResponse importSubjectResource(RemoteConnection connection,XnatSubjectdata subject, String resourceLabel, File zipFile ){
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"/resources/"+ resourceLabel ;
		         if (zipFile != null) {
			    	uri += "/files?overwrite=true&extract=true";
		         }
		    	 return this.importZipWithoutRetry( connection, uri, zipFile);
		    } catch (RuntimeException e) {
		    	try {
			    	logger.debug("importsubjectresource: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * import Project Resource with retry.
	 *
	 * @param connection the connection
	 * @param projectId the Project ID
	 * @return response
	 */
	public RemoteConnectionResponse importProjectResource(RemoteConnection connection,String projectId, String resourceLabel, File zipFile ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+projectId+"/resources/"+ resourceLabel +"/files?overwrite=true&extract=true";
		         return this.importZipWithoutRetry( connection, uri, zipFile);
		    } catch (RuntimeException e) {
		    	try {
			    	logger.debug("importsubjectresource: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * import Project Resource with retry.
	 *
	 * @param connection the connection
	 * @param experiment The experiment
	 * @param resourceLabel The resource label
	 * @param zipFile The zip file
	 * @return response
	 */
	public RemoteConnectionResponse importImageSessionResource(RemoteConnection connection,XnatExperimentdata experiment, String resourceLabel, File zipFile ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/experiments/"+experiment.getId()+"/resources/"+ resourceLabel +"/files?overwrite=true&extract=true";
		         return this.importZipWithoutRetry( connection, uri, zipFile);
		    } catch (RuntimeException e) {
		    	try {
			    	logger.debug("importsubjectresource: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}
	
	/**
	 * import SubjectAssessor Resource with retry.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @param assessor The assessor
	 * @param resourceLabel The resource label
	 * @param zipFile The zip file
	 * @return response
	 */
	public RemoteConnectionResponse importSubjectAssessorResource(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor, String resourceLabel, File zipFile ) throws Exception{
		int count = 0;
		while(true) {
		    try {
			    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"/experiments/"+assessor.getLabel()+"/resources/"+ resourceLabel +"/files?overwrite=true&extract=true";
			    	 return this.importZipWithoutRetry( connection, uri, zipFile);
		    	
		    } catch (RuntimeException e) {
		    	try {
			    	logger.debug("importsubjectresource: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (maxTries == 0 || ++count == maxTries) throw e;
		    }
		}
	}
	
	/**
	 * Import subject without retry.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @return true, if successful
	 */
	//TODO @Retryable(maxAttempts=5) update to retry when we upgrade spring to 4
	private RemoteConnectionResponse importSubjectWithoutRetry(RemoteConnection connection,XnatSubjectdata subject ) throws Exception{
		//do we need the assessor data and how.
		//MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();     
		
		// NOTE: Just call toXML on the subject object here, rather than calling getItem on the subject object and obtaining
		// XML from the item.  Using getItem() will query for experiments and assessors associated with the assession number.
		// In the case where there is a source-side subject with the same assession number as the destination-side subject,
		// this could result in the wrong sessions being sent to the destination, potentially overwriting previously synced
		// sessions with sessions from a subject outside the source project.
		final StringWriter tsw = new StringWriter();
		subject.toXML(tsw);
		tsw.close();
		final String subjectXml = tsw.toString();
		
		ResponseEntity<String> response = null;
		try {
			logger.debug("URL: " + connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?inbody=true");
			final HttpEntity<?> httpEntity = new HttpEntity<>(subjectXml, RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
			logger.debug(response.toString());
		} catch (XsyncHttpAuthenticationException authex) {
			try {
				final HttpEntity<?> httpEntity = new HttpEntity<>(subjectXml, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
				response = getResttemplate().exchange(connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
				logger.debug(response.toString());
			}catch(Exception e) {
				logger.error(ExceptionUtils.getStackTrace(e));
				logger.debug("Error while storing subject " + e.getMessage());
				String cachePath = SynchronizationManager.GET_SYNC_FILE_PATH(subject.getProject());
				File subjectF = new File(cachePath + "failed_" + subject.getLabel()+".xml");
				if (!subjectF.getParentFile().exists())
					subjectF.getParentFile().mkdirs();
				FileWriter fw = new FileWriter(subjectF);
				subject.toXML(fw, false);
				fw.close();
				throw e;
			}
		} catch (HttpClientErrorException clientEx) {
			// In the case of shared sessions, we get a 409 Conflict when posting XML.  In such cases, let's do a GET.  This will return
			// XML that will need to be handled later.
			if (clientEx.getMessage().contains("409 Conflict")) { 
				try {
					logger.debug("RECEIVED CONFLICT RESPONSE (shared subject) - Try GET operation to return subject XML");
					logger.debug("URL: "+connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?format=xml");
					final HttpEntity<?> httpEntity = new HttpEntity<>(null, RemoteConnectionManager.GetAuthHeaders(connection, true));
					response = getResttemplate().exchange(connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?format=xml", HttpMethod.GET, httpEntity, String.class);
				}catch(Exception e) {
					logger.debug("importSubjectWithoutRetry - HttpClientErrorException thrown - ", e);
					if (response != null) {
						logger.debug(response.toString());
					}
					throw e;
				}
			} else {
				logger.debug("importSubjectWithoutRetry - Exception thrown - ", clientEx);
				if (response != null) {
					logger.debug(response.toString());
				}
				//logger.debug(subjectXml);
				throw clientEx;
			}
		} catch (Exception e) {
			logger.debug("importSubjectWithoutRetry - Exception thrown - ", e);
			if (response != null) {
				logger.debug(response.toString());
			}
			//logger.debug(subjectXml);
			throw e;
		}
		//logger.debug(response.toString());
		//return 	((response.getStatusCode().value()==HttpStatus.OK.value()) || (response.getStatusCode().value()==HttpStatus.CREATED.value()))?true:false;
		return new RemoteConnectionResponse(response);
	}
	
	/**
	 * Delete subject without retry.
	 *
	 * @param connection the connection
	 * @param uri The URI
	 * @return response
	 */
	//TODO @Retryable(maxAttempts=5) update to retry when we upgrade spring to 4
	private RemoteConnectionResponse deleteWithoutRetry(RemoteConnection connection, String uri ) throws Exception{
		//do we need the assessor data and how.
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(uri, HttpMethod.DELETE, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException authex) {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, false, true));
			response = getResttemplate().exchange(uri, HttpMethod.DELETE, httpEntity, String.class);
		}
		logger.info(truncateStr(response));
		logger.info(truncateStr(response.getBody()));
		logger.info(truncateStr(response.getHeaders().get("Set-Cookie")));
		return new RemoteConnectionResponse(response);
	}
	
	
	/**
	 * import subject assessor with retry.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @param assessor the assessor
	 * @return true, if successful
	 */
	public RemoteConnectionResponse importSubjectAssessor(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor ) throws Exception{
		int count = 0;
			while(true) {
			    try {

			         return this.importSubjectAssessorWithoutRetry(  connection,  subject, assessor );
			    } catch (RuntimeException e) {
			    	try {
				    	logger.debug("importSubjectAssessor: retrycount "+ count);
						if (maxTries > 0) Thread.sleep(sleep);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
			        // handle exception
			        if (maxTries == 0 || ++count == maxTries) throw e;
			    }
			}
		}
	
	/**
	 * Import subject assessor without retry.
	 *
	 * @param connection the connection
	 * @param subject the subject
	 * @param assessor the assessor
	 * @return true, if successful
	 */
	private RemoteConnectionResponse importSubjectAssessorWithoutRetry(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor ) throws Exception{
		String assessorXml=assessor.getItem().toXML_String();
		
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<>(assessorXml, RemoteConnectionManager.GetAuthHeaders(connection, true));
			final RestTemplate restTemplate = new RestTemplate();
			restTemplate.setErrorHandler(new XsyncResponseErrorHandler());
			response = restTemplate.exchange(connection.getUrl()+"/data/archive/projects/"+assessor.getProject()+"/subjects/"+subject.getLabel()+"/experiments/"+assessor.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException e) {
			final HttpEntity<?> httpEntity = new HttpEntity<>(assessorXml, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
			final RestTemplate restTemplate = new RestTemplate();
			restTemplate.setErrorHandler(new XsyncResponseErrorHandler());
			response = restTemplate.exchange(connection.getUrl()+"/data/archive/projects/"+assessor.getProject()+"/subjects/"+subject.getLabel()+"/experiments/"+assessor.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
		} 
		//return  ((response.getStatusCode().value()==HttpStatus.OK.value()) || (response.getStatusCode().value()==HttpStatus.CREATED.value()))?true:false;
		return new RemoteConnectionResponse(response);
	}
	
	/**
	 * Get URI result.
	 *
	 * @param connection the connection
	 * @param uri the uri
	 * @return ResponseEntity wrapper
	 */
	public RemoteConnectionResponse getResult(RemoteConnection connection,String uri) throws Exception{
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(uri, HttpMethod.GET, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException e) {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, false, true));
			response = getResttemplate().exchange(uri, HttpMethod.GET, httpEntity, String.class);
		}
		logger.info(truncateStr(response));
		logger.info(truncateStr(response.getBody()));
		logger.info(truncateStr(response.getHeaders().get("Set-Cookie")));
		return new RemoteConnectionResponse(response);
	}
	
	public String truncateStr(Object obj) {
		return truncateStr(obj,TRUNCATE_LOG_OUTPUT_LENGTH);
	}
	
	public String truncateStr(Object obj, int maxlength) {
		if (obj==null) {
			return null;
		}
		return (obj.toString().length()>maxlength) ? obj.toString().substring(0,maxlength) + "......." : obj.toString();
	}

	private static final List<HttpStatus> COMPLETED_STATUSES = Arrays.asList(HttpStatus.OK, HttpStatus.CREATED);
	private static final List<HttpStatus> ERROR_STATUSES = Arrays.asList(HttpStatus.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR);

}
