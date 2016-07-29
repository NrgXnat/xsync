package org.nrg.xsync.services.remote;


import java.io.File;

import org.apache.log4j.Logger;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;


/**
 * The Class RemoteRESTServiceImpl.
 */
@Service
//@EnableRetry
//TODO update to retry when we upgrade spring to 4
public class RemoteRESTServiceImpl  extends AbstractRemoteRESTService implements RemoteRESTService {
	
	/** The logger. */
	public static Logger logger = Logger.getLogger(RemoteRESTServiceImpl.class);

	// TODO Should these be part of xsync site config?
	/** The max tries. */
	static int maxTries = 2;
	
	/** The sleep. */
	static long sleep=10000;
	
	/**
	 * Instantiates a new remote rest service impl.
	 */
	public  RemoteRESTServiceImpl() {
		super();
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
		         return this.importXarWithoutRetry(connection, xar);
		    } catch (RuntimeException e) {
		    	try {
		    		logger.error("importXar: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
		    }
		}
	}
	
	/**
	 * Import xar without retry.
	 *
	 * @param connection the connection
	 * @param payload the payload
	 * @param xar the xar
	 * @return true, if successful
	 * @throws RuntimeException the runtime exception
	 */
	//@Retryable(maxAttempts=5,value=RuntimeException.class,backoff= @Backoff(delay=100, maxDelay=500))
	//TODO update to retry when we upgrade spring to 4
	private RemoteConnectionResponse importXarWithoutRetry(RemoteConnection connection,  File xar) throws RuntimeException{
		//this.setAliasToken(connection);
		final MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();     
		body.add("field", "value");
		body.add("import-handler","XAR");
		body.add("file", new FileSystemResource(xar));
		
		ResponseEntity<String> response;
		try {
			try {
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, RemoteConnectionManager.GetAuthHeaders(connection, true));
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import", HttpMethod.POST, httpEntity, String.class);
			} catch (XsyncHttpAuthenticationException authex) {
				final HttpEntity<?> httpEntity = new HttpEntity<Object>(body, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
				response = getResttemplate().exchange(connection.getUrl()+"/data/services/import", HttpMethod.POST, httpEntity, String.class);
			}
			logger.info(response);
			logger.info(response.getBody());
			logger.info(response.getHeaders().get("Set-Cookie"));
			final boolean status= ((response.getStatusCode().value()==HttpStatus.OK.value()) || 
						 (response.getStatusCode().value()==HttpStatus.CREATED.value()) ||
						 // Let's not keep trying these error types either.  They will be thrown by invalid XAR requests, and we don't want a
						 // long wait with retry for errors returned by the XarImporter class.
						 (response.getStatusCode().value()==HttpStatus.INTERNAL_SERVER_ERROR.value()) || 
						 (response.getStatusCode().value()==HttpStatus.BAD_REQUEST.value())
						 )?true:false;
			logger.warn("importXar"+xar.getName());
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
		
		final MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();     
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
		logger.info(response);
		logger.info(response.getBody());
		logger.info(response.getHeaders().get("Set-Cookie"));
		boolean status= ((response.getStatusCode().value()==HttpStatus.OK.value()) || (response.getStatusCode().value()==HttpStatus.CREATED.value()))?true:false;
		logger.warn("importZip"+zip.getName());
		if(!status){
			throw new RuntimeException("importZip request failed. Retrying...");
		}else{
			return new RemoteConnectionResponse(response);
		}
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
			    	logger.error("importSubject: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * delete Subject without retry.
	 *
	 * @param connection the connection
	 * @param subjectId the subject ID
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
			    	logger.error("deleteSubject: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * delete Experiment without retry.
	 *
	 * @param connection the connection
	 * @param experiment 
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
			    	logger.error("Could not find a subject id " + experiment.getLabel());
			    }
			    if (subjectId != null) { 
			    	String uri = connection.getUrl()+"/data/archive/projects/"+experiment.getProject()+"/subjects/"+subjectId+"/experiments/"+experiment.getId()+"?removeFiles=true";
			    	return this.deleteWithoutRetry( connection,uri);
			    }
		    } catch (Exception e) {
		    	try {
			    	logger.error("deleteSubject: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
		    }
		}
	}

	/**
	 * delete Subject Resource with retry.
	 *
	 * @param connection the connection
	 * @param subjectId the subject ID
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
			    	logger.error("deleteSubject: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
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
			    	logger.error("deleteProjectResource: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
		    }
		}
	}

	
	/**
	 * import Subject Resource with retry.
	 *
	 * @param connection the connection
	 * @param subjectId the subject ID
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
			    	logger.error("importsubjectresource: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
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
			    	logger.error("importsubjectresource: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
		    }
		}
	}

	
	/**
	 * import SubjectAssessor Resource with retry.
	 *
	 * @param connection the connection
	 * @param subjectId the subject ID
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
			    	logger.error("importsubjectresource: retrycount "+ count);
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
		        // handle exception
		        if (++count == maxTries) throw e;
		    }
		}
	}
	
	/**
	 * Import subject without retry.
	 *
	 * @param connection the connection
	 * @param payload the payload
	 * @param subject the subject
	 * @return true, if successful
	 */
	//TODO @Retryable(maxAttempts=5) update to retry when we upgrade spring to 4
	private RemoteConnectionResponse importSubjectWithoutRetry(RemoteConnection connection,XnatSubjectdata subject ) throws Exception{
		//do we need the assessor data and how.
		//MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();     
		final String subjectXml=subject.getItem().toXML_String();
		
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<String>(subjectXml, RemoteConnectionManager.GetAuthHeaders(connection, true));
			response = getResttemplate().exchange(connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException authex) {
			final HttpEntity<?> httpEntity = new HttpEntity<String>(subjectXml, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
			response = getResttemplate().exchange(connection.getUrl()+"/data/archive/projects/"+subject.getProject()+"/subjects/"+subject.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
		}
		
		logger.info(response);
		//return 	((response.getStatusCode().value()==HttpStatus.OK.value()) || (response.getStatusCode().value()==HttpStatus.CREATED.value()))?true:false;
		return new RemoteConnectionResponse(response);
	}
	
	/**
	 * Delete subject without retry.
	 *
	 * @param connection the connection
	 * @param subjectId the subject ID
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
		logger.info(response);
		logger.info(response.getBody());
		logger.info(response.getHeaders().get("Set-Cookie"));
		return new RemoteConnectionResponse(response);
	}
	
	
	/**
	 * import subject assessor with retry.
	 *
	 * @param connection the connection
	 * @param payload the payload
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
				    	logger.error("importSubjectAssessor: retrycount "+ count);
						Thread.sleep(sleep);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
			        // handle exception
			        if (++count == maxTries) throw e;
			    }
			}
		}
	
	/**
	 * Import subject assessor without retry.
	 *
	 * @param connection the connection
	 * @param payload the payload
	 * @param subject the subject
	 * @param assessor the assessor
	 * @return true, if successful
	 */
	private RemoteConnectionResponse importSubjectAssessorWithoutRetry(RemoteConnection connection,XnatSubjectdata subject,XnatSubjectassessordata assessor ) throws Exception{
		String assessorXml=assessor.getItem().toXML_String();
		
		ResponseEntity<String> response;
		try {
			final HttpEntity<?> httpEntity = new HttpEntity<String>(assessorXml, RemoteConnectionManager.GetAuthHeaders(connection, true));
			final RestTemplate restTemplate = new RestTemplate();
			restTemplate.setErrorHandler(new XsyncResponseErrorHandler());
			response = restTemplate.exchange(connection.getUrl()+"/data/archive/projects/"+assessor.getProject()+"/subjects/"+subject.getLabel()+"/experiments/"+assessor.getLabel()+"?inbody=true", HttpMethod.PUT, httpEntity, String.class);
		} catch (XsyncHttpAuthenticationException e) {
			final HttpEntity<?> httpEntity = new HttpEntity<String>(assessorXml, RemoteConnectionManager.GetAuthHeaders(connection, false, true));
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
		logger.info(response);
		logger.info(response.getBody());
		logger.info(response.getHeaders().get("Set-Cookie"));
		return new RemoteConnectionResponse(response);
	}	
		
}
