package org.nrg.xsync.services.remote;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.nrg.framework.services.SerializerService;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xsync.components.XsyncSitePreferencesBean;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.manager.SynchronizationManager;
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
import org.apache.http.entity.mime.MultipartEntityBuilder;

import javax.annotation.PostConstruct;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * The Class RemoteRESTServiceImpl.
 */
@Service
@Slf4j
public class RemoteRESTServiceImpl  extends AbstractRemoteRESTService implements RemoteRESTService {
	// TODO:  Do we want this to be configurable?
	public static final int TRUNCATE_LOG_OUTPUT_LENGTH = 1000;

	private final XsyncSitePreferencesBean _prefs;
	private final ObjectMapper _objectMapper;
	private long sleep = 10;
	private int maxTries = 1;


	@Autowired
	public RemoteRESTServiceImpl(final XsyncSitePreferencesBean prefs, final SerializerService serializerService) {
		_prefs = prefs;
		_objectMapper = serializerService.getObjectMapper();
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
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import?import-handler=XAR&localFilePath="
						+ xarPath + "&removeLocalFileAfterImport=true", HttpMethod.POST, httpEntity, String.class);
			} catch (XsyncHttpAuthenticationException authex) {
				final HttpHeaders header = RemoteConnectionManager.GetAuthHeaders(connection, false, true);
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import?import-handler=XAR&localFilePath="
						+ xarPath + "&removeLocalFileAfterImport=true", HttpMethod.POST, httpEntity, String.class);
			}
			log.info(truncateStr(response));
			log.info(truncateStr(response.getBody()));
			log.info(truncateStr(response.getHeaders().get("Set-Cookie")));
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
			if (e instanceof NestedRuntimeException) {
				final Throwable specCause = ((NestedRuntimeException)e).getMostSpecificCause();
				// Let's not keep trying these error types either.  They will be thrown by invalid XAR requests, and we don't want a
				// long wait with retry for errors returned by the XarImporter class.
				if (specCause instanceof HttpServerErrorException) {
					return new RemoteConnectionResponse(new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR));
				}
			}
			throw(e);
		}finally {
			tempFile.delete();
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
	public RemoteConnectionResponse importXar(final RemoteConnection connection, final File xar) throws RuntimeException{
		int count = 0;
		while(true) {
		    try {
		    	 log.debug("Attempting importXar: File={}", xar.getName());
		         return importXarWithoutRetry(connection, xar);
		    } catch (RuntimeException e) {
		    	count++;
	    		log.error("Exception thrown during importXar", e);
		        if (maxTries == 0 || count > maxTries) {
		        	throw e;
				} else {
					log.error("importXar will be reattempted in {} seconds.", sleep/1000);
				}
		    	try {
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
		    		// Ignore
				}
	    		log.error("Retrying importXar: retry count {} out of {}", count, maxTries);
		    	connection.useRefreshedAliasToken();
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
	private RemoteConnectionResponse importXarWithoutRetry(RemoteConnection connection, File xar)
			throws RuntimeException {
		final long fileSize = xar.length();
		final boolean doAsync = fileSize > 5*1024*1024; // > 5MB

		final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("field", "value");
		body.add("import-handler", "XAR");

		String listener = null;
		if (doAsync) {
			listener = LISTENER_FMT.format(new Date());
			String cacheUrl = "/user/cache/resources/" + listener + "/files/" + xar.getName();
			uploadXarToCache(connection, xar, cacheUrl);
			body.add("http-session-listener", listener);
			body.add("src", cacheUrl);
		} else {
			body.add("file", new FileSystemResource(xar));
		}

		ResponseEntity<String> response;
		try {
			try {
				HttpHeaders header = RemoteConnectionManager.GetAuthHeaders(connection, true);
				header.setContentType(MediaType.MULTIPART_FORM_DATA);
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				log.info("importXar file={} length={} async={}", xar.getAbsolutePath(), fileSize, doAsync);
				long startTime= System.currentTimeMillis();
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import",
						HttpMethod.POST, httpEntity, String.class);
				long endTime = System.currentTimeMillis();
				log.debug("Total time to import: {} ms", endTime - startTime);
			} catch (XsyncHttpAuthenticationException authex) {
				HttpHeaders header = RemoteConnectionManager.GetAuthHeaders(connection, false, true);
				header.setContentType(MediaType.MULTIPART_FORM_DATA);
				log.debug("Retrying after getting Authentication headers");
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, header);
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import",
						HttpMethod.POST, httpEntity, String.class);
			}
			log.info("Response: {}\n{}\n{}", truncateStr(response), truncateStr(response.getBody()),
					truncateStr(response.getHeaders().get("Set-Cookie")));

			// Tests for Bad Request and Internal Server Error in addition to OK and Created.  Those error statues will
			// be thrown by invalid XAR requests, and we don't want a long wait with retry for errors returned by the
			// XarImporter class.
			final HttpStatus statusCode = response.getStatusCode();
			final boolean    status     = COMPLETED_STATUSES.contains(statusCode) || ERROR_STATUSES.contains(statusCode);
			if(!status){
				throw new RuntimeException("importXar request failed. Retrying...");
			}
		} catch (RuntimeException e) {
			log.error("importXar process failed for {}", xar.getAbsolutePath(), e);
			if (e instanceof NestedRuntimeException) {
				final Throwable specCause = ((NestedRuntimeException)e).getMostSpecificCause();
				// Let's not keep trying these error types either.  They will be thrown by invalid XAR requests, and we don't want a
				// long wait with retry for errors returned by the XarImporter class.
				if (specCause instanceof HttpServerErrorException) {
					return new RemoteConnectionResponse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
				}
			}
			throw e;
		}

		if (doAsync) {
			return new RemoteConnectionResponse(monitorAsyncImport(connection, listener));
		} else {
			return new RemoteConnectionResponse(response);
		}
	}

	private void uploadXarToCache(final RemoteConnection connection, final File xar, final String cacheUrl) {
		final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		builder.addBinaryBody("file",  xar, ContentType.create("application/xar"), xar.getName());
		String url = connection.getUrl() + "/data" + cacheUrl;

		final org.apache.http.HttpEntity entity = builder.build();
		HttpUriRequest httpPut = RequestBuilder.put(url)
				.setEntity(entity)
				.build();
		CloseableHttpResponse response = null;
		RequestConfig requestConfig = RequestConfig.custom().setExpectContinueEnabled(true).build();
		CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

		try {
			RemoteConnectionManager.addAuthHeaders(connection, httpPut, true);
			log.info("Uploading {} to user cache dir {}", xar.getAbsolutePath(), cacheUrl);
			long startTime= System.currentTimeMillis();
			response = httpClient.execute(httpPut);
			long endTime = System.currentTimeMillis();
			log.debug("Total time to upload: {} ms", endTime - startTime);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 401) {
				throw new XsyncHttpAuthenticationException(HttpStatus.valueOf(statusCode), response.getStatusLine().getReasonPhrase());
			}
		} catch (XsyncHttpAuthenticationException authex) {
			RemoteConnectionManager.addAuthHeaders(connection, httpPut, false, true);
			try {
				response = httpClient.execute(httpPut);
			} catch(IOException e) {
				log.error("Can not establish connection {}", e.getMessage());
				throw new RuntimeException("Upload Xar to cache request failed. Retrying...");
			}
		} catch (IOException ioe) {
			log.error("Can not establish connection {}", ioe.getMessage());
			throw new RuntimeException("importZip request failed. Retrying...");
		}finally {
			try {
				response.close();
				httpClient.close();
			} catch(Exception e) {
				throw new RuntimeException("importZip request failed. Retrying...");
			}
		}
		final int statusCode = response.getStatusLine().getStatusCode();
		log.debug("Response status uploadXarToCache: " + statusCode);
		if (!COMPLETED_STATUSES.contains(HttpStatus.valueOf(statusCode))) {
			throw new RuntimeException("Failed to upload " + xar.getAbsolutePath() + ": " + response);
		}
	}

	private ResponseEntity<String> monitorAsyncImport(final RemoteConnection connection, final String listener) {
		boolean succeeded = false;
		String finalMsg = null;
		String url = connection.getUrl() + "/xapi/event_tracking/" + listener;
		try {
			final long start = System.currentTimeMillis();
			int count = 0, tries = 3;
			do {
				RemoteConnectionResponse response = null;
				try {
					response = getResult(connection, url);
					if (!response.wasSuccessful()) {
						throw new HttpClientErrorException(response.getResponse().getStatusCode());
					}
					String json = response.getResponseBody();
					log.debug("Import progress: {}", json);
					JsonNode jsonNode = _objectMapper.readTree(json);
					// succeeded is null while processing is running, so sleep and keep polling
					JsonNode successNode = jsonNode.get("succeeded");
					if (!(successNode instanceof NullNode)) {
						// succeeded is T or F: either way, we can break out of this loop once we collect the final message
						succeeded = successNode.asBoolean();
						finalMsg = jsonNode.get("finalMessage").asText();
						break;
					}
				} catch (HttpClientErrorException e) {
					// We get 404s before progress tracking starts, treat these differently
					// (maybe the XNAT is slammed and import thread hasn't started)
					if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
						if (count < tries) {
							count++;
						} else {
							// something is going wrong, break out
							log.error("Issue polling import progress: {}", response, e);
							finalMsg = "Polling failed: " + e.getResponseBodyAsString();
							break;
						}
					}
				}
				try {
					Thread.sleep(5000);
				} catch (InterruptedException interruptedException) {
					// Ignore
				}
			} while (System.currentTimeMillis() - start < TimeUnit.HOURS.toMillis(2));
		} catch (Exception e) {
			log.error("Exception when polling import progress", e);
			throw new RuntimeException(e);
		}

		if (!succeeded) {
			throw new RuntimeException(StringUtils.defaultIfBlank(finalMsg,
					"Import did not complete within 2 hours"));
		}

		return new ResponseEntity<>(finalMsg, HttpStatus.OK);
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
	private RemoteConnectionResponse importZipWithoutRetry(final RemoteConnection connection, final  String uri, final File zip) throws RuntimeException{
		if (null != zip) {
			log.info("Attempting to send zip file: {} ", zip.getName());
			final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
			builder.addBinaryBody("file",  zip, ContentType.create("application/zip"), zip.getName());
			final org.apache.http.HttpEntity entity = builder.build();
			HttpUriRequest httpPost = RequestBuilder.post(uri)
					.setEntity(entity)
					.build();
			int statusCode = -1;
			CloseableHttpResponse response = null;
			RequestConfig requestConfig = RequestConfig.custom().setExpectContinueEnabled(true).build();
			CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

			try {
				try {
					RemoteConnectionManager.addAuthHeaders(connection, httpPost, true);
					log.info("Set the authentication headers. About to send file");
					response = httpClient.execute(httpPost);
					statusCode = response.getStatusLine().getStatusCode();
					log.info("File {} sent. Status: {} ", zip.getName(), statusCode);
					if (statusCode == 401) {
						throw new XsyncHttpAuthenticationException(HttpStatus.valueOf(statusCode), response.getStatusLine().getReasonPhrase());
					}
				} catch(XsyncHttpAuthenticationException authex) {
					log.error("Authrorization Failed. Attempting with fresh token.");
					RemoteConnectionManager.addAuthHeaders(connection, httpPost, false, true);
					try {
						response = httpClient.execute(httpPost);
						statusCode = response.getStatusLine().getStatusCode();
					} catch(IOException e) {
						log.error("Can not establish connection {}", e.getMessage());
						throw new RuntimeException("importZip request failed. Retrying...");
					}
				} catch(IOException ioe) {
					log.error("Can not establish connection {}", ioe.getMessage());
					throw new RuntimeException("importZip request failed. Retrying...");
				}
				log.info(truncateStr(response));
				log.info("Got Status: {}", statusCode);
				final boolean status = COMPLETED_STATUSES.contains(HttpStatus.valueOf(statusCode));
				if (!status) {
					throw new RuntimeException("importZip request failed. Retrying...");
				} else {
					return new RemoteConnectionResponse(response);
				}
			} catch(Exception e) {
				log.error("Exception encountered {} for file {}", e.getMessage(),  zip.getAbsolutePath());
				throw new RuntimeException("importZip request failed.", e);
			} finally {
				try {
					response.close();
					httpClient.close();
				} catch(Exception e) {
					throw new RuntimeException("importZip could not close connections", e);
				}
			}
		} else {
			log.error("Zip file appears to be null");
			throw new RuntimeException("Zip File is null");
		}
	}



	/**
	 * Create Workflow with retry.
	 *
	 * @param connection the connection
	 * @return response
	 */
	public RemoteConnectionResponse createWorkflow(final RemoteConnection connection, final WrkWorkflowdata wrk ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		         return this.createWorkflowWithoutRetry( connection, wrk );
		    } catch (RuntimeException e) {
		    	try {
		    		log.debug("Exception " + e.getMessage());
			    	if (maxTries > 0) {
				    	log.error("createWorkflow: retrycount "+ count);
				    	log.error("Referesh rate is " + _prefs.getSyncRetryCountInt());
				    	log.error("Referesh rate is " + _prefs.getSyncRetryInterval());
				    	log.error("Sleeping for " + sleep + " milliseconds");
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
	private RemoteConnectionResponse createWorkflowWithoutRetry(final RemoteConnection connection, final WrkWorkflowdata wrk ) throws Exception{
		//do we need the assessor data and how.
		//MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();     
		final String wrkXml=wrk.getItem().toXML_String();
		
		ResponseEntity<String> response;
		try {
			log.debug("URL: " + connection.getUrl()+"/data/workflows?req_format=xml");
			final HttpEntity<?> httpEntity = new HttpEntity<>(wrkXml, RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(connection.getUrl()+"/data/workflows?req_format=xml", HttpMethod.PUT, httpEntity, String.class);
			log.debug(response.toString());
		} catch (XsyncHttpAuthenticationException authex) {
			try {
				final HttpEntity<?> httpEntity = new HttpEntity<>(wrkXml, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
				response = getResttemplate().exchange(connection.getUrl()+"/data/workflows?req_format=xml", HttpMethod.PUT, httpEntity, String.class);
				log.debug(response.toString());
			}catch(Exception e) {
				log.debug("Error while storing workflow " + e.getMessage());
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
		
		log.debug(response.toString());
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
	public RemoteConnectionResponse importSubject(final RemoteConnection connection, final XnatSubjectdata subject) throws Exception{
		int count = 0;
		while(true) {
		    try {
		         return this.importSubjectWithoutRetry( connection, subject );
		    } catch (RuntimeException e) {
				// handle exception
				if (count >= maxTries) throw e;
		    	try {
		    		log.error("Exception in importSubject: retrycount {} of {}, sleeping for {}s",
							count, maxTries, sleep/1000, e);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					// Ignore
				}
		    	count++;
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
	public RemoteConnectionResponse deleteSubject(final RemoteConnection connection, final XnatSubjectdata subject ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"?removeFiles=true";
		         return this.deleteWithoutRetry( connection,uri);
		    } catch (RuntimeException e) {
		    	try {
			    	log.error("deleteSubject: retrycount "+ count);
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
	public RemoteConnectionResponse deleteExperiment(final RemoteConnection connection, final XnatExperimentdata experiment ) throws Exception {
		int count = 0;
		while(true) {
		    try {
		    	String subjectId = null;
			    try {
			    	subjectId = (String)experiment.getItem().getProperty("subject_ID");
			    }catch(Exception e1) {
			    	log.error("Could not find a subject id " + experiment.getLabel(),e1);
			    }
			    if (subjectId != null) { 
			    	String uri = connection.getUrl()+"/data/archive/projects/"+experiment.getProject()+"/subjects/"+subjectId+"/experiments/"+experiment.getId()+"?removeFiles=true";
			    	return this.deleteWithoutRetry( connection,uri);
			    }
		    } catch (Exception e) {
		    	try {
			    	log.error("deleteSubject: retrycount "+ count);
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
	public RemoteConnectionResponse deleteSubjectResource(final RemoteConnection connection, final XnatSubjectdata subject, final String resourceLabel ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"/resources/"+ resourceLabel +"?removeFiles=true";
		         return this.deleteWithoutRetry( connection, uri);
		    } catch (RuntimeException e) {
		    	try {
			    	log.error("deleteSubject: retrycount "+ count);
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
	public RemoteConnectionResponse deleteProjectResource(final RemoteConnection connection, final String projectId, final String resourceLabel ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+projectId+"/resources/"+ resourceLabel +"?removeFiles=true";
		         return this.deleteWithoutRetry( connection, uri);
		    } catch (RuntimeException e) {
		    	try {
			    	log.error("deleteProjectResource: retrycount "+ count);
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
	public RemoteConnectionResponse importSubjectResource(final RemoteConnection connection, final XnatSubjectdata subject, final String resourceLabel, final File zipFile, final boolean updateStats){
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"/resources/"+ resourceLabel ;
		         if (zipFile != null) {
			    	uri += "/files?overwrite=true&extract=true&update-stats=" + updateStats;
		         }
		    	 return this.importZipWithoutRetry(connection, uri, zipFile);
		    } catch (RuntimeException e) {
		    	try {
			    	log.error("importsubjectresource: retrycount "+ count);
					if (maxTries > 0) Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					log.error("Could not send data", e1);
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
	public RemoteConnectionResponse importProjectResource(final RemoteConnection connection, final String projectId, final String resourceLabel, final File zipFile, final boolean updateStats ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/projects/"+projectId+"/resources/"+ resourceLabel +"/files?overwrite=true&extract=true&update-stats=" + updateStats;
		         return this.importZipWithoutRetry( connection, uri, zipFile);
		    } catch (RuntimeException e) {
		    	try {
			    	log.error("importsubjectresource: retrycount "+ count);
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
	public RemoteConnectionResponse importImageSessionResource(final RemoteConnection connection, final XnatExperimentdata experiment, final String resourceLabel, final File zipFile, final boolean updateStats ) throws Exception{
		int count = 0;
		while(true) {
		    try {
		    	 String uri = connection.getUrl()+"/data/archive/experiments/"+experiment.getId()+"/resources/"+ resourceLabel +"/files?overwrite=true&extract=true&update-stats=" + updateStats;
		         return this.importZipWithoutRetry( connection, uri, zipFile);
		    } catch (RuntimeException e) {
		    	try {
			    	log.error("importsubjectresource: retrycount "+ count);
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
	public RemoteConnectionResponse importSubjectAssessorResource(final RemoteConnection connection, final XnatSubjectdata subject, final XnatSubjectassessordata assessor, final String resourceLabel, final File zipFile, final boolean updateStats ) throws Exception{
		int count = 0;
		while(true) {
		    try {
			    	 String uri = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getId()+"/experiments/"+assessor.getLabel()+"/resources/"+ resourceLabel +"/files?overwrite=true&extract=true&update-stats=" + updateStats;
			    	 return this.importZipWithoutRetry( connection, uri, zipFile);
		    } catch (RuntimeException e) {
		    	try {
			    	log.error("importsubjectresource: retrycount "+ count);
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
	private RemoteConnectionResponse importSubjectWithoutRetry(final RemoteConnection connection, final XnatSubjectdata subject ) throws Exception{
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
			String url = connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?inbody=true";
			log.debug("URL: {}", url);
			final HttpEntity<?> httpEntity = new HttpEntity<>(subjectXml, RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(url, HttpMethod.PUT, httpEntity, String.class);
			log.debug(response.toString());
		} catch (XsyncHttpAuthenticationException authex) {
			try {
				final HttpEntity<?> httpEntity = new HttpEntity<>(subjectXml, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
				response = getResttemplate().exchange(connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
				log.debug(response.toString());
			}catch(Exception e) {
				log.error(ExceptionUtils.getStackTrace(e));
				log.debug("Error while storing subject " + e.getMessage());
				String cachePath = SynchronizationManager.GET_SYNC_FILE_PATH(subject.getProject());
				File subjectF = new File(cachePath + "failed_" + subject.getLabel()+".xml");
				if (!subjectF.getParentFile().exists())
					subjectF.getParentFile().mkdirs();
				FileWriter fw = new FileWriter(subjectF);
				subject.toXML(fw, false);
				fw.close();
				throw e;
			}
		} catch (Exception e) {
			log.debug("importSubjectWithoutRetry - Exception thrown - ", e);
			if (response != null) {
				log.debug(response.toString());
			}
			//log.debug(subjectXml);
			throw e;
		}
		
		log.debug(response.toString());
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
	private RemoteConnectionResponse deleteWithoutRetry(final RemoteConnection connection, final String uri ) throws Exception{
		//do we need the assessor data and how.
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(uri, HttpMethod.DELETE, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException authex) {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, false, true));
			response = getResttemplate().exchange(uri, HttpMethod.DELETE, httpEntity, String.class);
		}
		log.info(truncateStr(response));
		log.info(truncateStr(response.getBody()));
		log.info(truncateStr(response.getHeaders().get("Set-Cookie")));
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
	public RemoteConnectionResponse importSubjectAssessor(final RemoteConnection connection, final XnatSubjectdata subject, final XnatSubjectassessordata assessor ) throws Exception{
		int count = 0;
			while(true) {
			    try {

			         return this.importSubjectAssessorWithoutRetry(  connection,  subject, assessor );
			    } catch (RuntimeException e) {
			    	try {
				    	log.error("importSubjectAssessor: retrycount "+ count);
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
	private RemoteConnectionResponse importSubjectAssessorWithoutRetry(final RemoteConnection connection, final XnatSubjectdata subject, final XnatSubjectassessordata assessor ) throws Exception{
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
	public RemoteConnectionResponse getResult(final RemoteConnection connection, final String uri) {
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(uri, HttpMethod.GET, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException e) {
			final HttpEntity<?> httpEntity = new HttpEntity<Object>(RemoteConnectionManager.GetAuthHeaders(connection, false, true));
			response = getResttemplate().exchange(uri, HttpMethod.GET, httpEntity, String.class);
		}
		log.info(truncateStr(response));
		log.info(truncateStr(response.getBody()));
		log.info(truncateStr(response.getHeaders().get("Set-Cookie")));
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
	private static final DateFormat LISTENER_FMT = new SimpleDateFormat("yyyyMMdd_HHmmssS");
}
