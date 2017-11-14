/**
 * 
 */
package org.nrg.xnat.xsync.report;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.nrg.framework.services.SerializerService;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.nrg.xsync.connection.RemoteConnection;
import org.nrg.xsync.connection.RemoteConnectionHandler;
import org.nrg.xsync.connection.RemoteConnectionManager;
import org.nrg.xsync.connection.RemoteConnectionResponse;
import org.nrg.xsync.exception.XsyncRemoteConnectionException;
import org.nrg.xsync.utils.QueryResultUtil;
import org.nrg.xsync.utils.XsyncUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.google.gson.Gson;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

/**
 * The Class XsyncProjectReportGenerator.
 *
 * @author Atul
 */
public class XsyncProjectReportGenerator {

	/** The Constant _log. */
	private static final Logger _log = LoggerFactory.getLogger(XsyncProjectReportGenerator.class);

	/** The project sync configuration. */
	private ProjectSyncConfiguration projectSyncConfiguration;

	/** The jdbc template. */
	private final NamedParameterJdbcTemplate _jdbcTemplate;

	/** The manager. */
	private final RemoteConnectionManager _manager;

	/** The query result util. */
	private final QueryResultUtil _queryResultUtil;

	/** The serializer. */
	private final SerializerService _serializer;

	/** The user. */
	private final UserI _user;

	/**
	 * Instantiates a new xsync project report generator.
	 *
	 * @param manager
	 *            the manager
	 * @param queryResultUtil
	 *            the query result util
	 * @param jdbcTemplate
	 *            the jdbc template
	 * @param projectSyncConfiguration
	 *            the project sync configuration
	 * @param serializer
	 *            the serializer
	 * @param user
	 *            the user
	 */
	public XsyncProjectReportGenerator(final RemoteConnectionManager manager, final QueryResultUtil queryResultUtil,
			final NamedParameterJdbcTemplate jdbcTemplate, ProjectSyncConfiguration projectSyncConfiguration,
			SerializerService serializer, final UserI user) {
		this.projectSyncConfiguration = projectSyncConfiguration;
		this._manager = manager;
		this._queryResultUtil = queryResultUtil;
		_jdbcTemplate = jdbcTemplate;
		_serializer = serializer;
		this._user = user;
	}

	/**
	 * Generate mapping report.
	 *
	 * @param reportFormat
	 *            the report format
	 * @return the string
	 * @throws Exception
	 *             the exception
	 */
	public String generateMappingReport(String reportFormat,String objectType) throws Exception {
		final String remoteUrl = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo()
				.getRemoteUrl();
		final String sourceProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB()
				.getSourceProjectId();
		final String remoteProjectId = projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo()
				.getRemoteProjectId();
		final String uri = remoteUrl + "/data/experiments?format=csv&project=" + remoteProjectId
				+ "&columns=ID,label,URI,subject_ID,subject_label";
		final RemoteConnectionResponse response = getResult(uri);
		if (!response.wasSuccessful()) {
			throw new XsyncRemoteConnectionException("ERROR:  Could not check remote site for data.");
		} else {
			CSVReader reader = new CSVReader(new StringReader(response.getResponseBody()), CSVParser.DEFAULT_SEPARATOR,
					CSVParser.DEFAULT_QUOTE_CHARACTER, 1);
			String[] data;
			Map<String, String> experimentDataMap = new TreeMap<String, String>();
			Map<String, String> subjectMap = new TreeMap<String, String>();
			while ((data = reader.readNext()) != null) {
				experimentDataMap.put(data[0], data[4]);
				subjectMap.put(data[1], data[2]);
			}
			reader.close();

			List<Map<String, Object>> resultList = new XsyncUtils(_serializer, _jdbcTemplate, _user)
					.getIdAndLabelMapForSyncedData(sourceProjectId, objectType);
			if (reportFormat == null || reportFormat.equals(XsyncUtils.REPORT_FORMAT_CSV)) {
				return getReportCSV(experimentDataMap, subjectMap, resultList,objectType);
			} else if (reportFormat.equals(XsyncUtils.REPORT_FORMAT_JSON)) {
				return getReportJSON(experimentDataMap, subjectMap, resultList,objectType);
			} else {
				throw new Exception("Invalid report format specified.  Valid formats are CSV and JSON.");
			}
		}
	}

	/**
	 * Gets the report CSV.
	 *
	 * @param experimentDataMap
	 *            the experiment data map
	 * @param subjectMap
	 *            the subject map
	 * @param resultList
	 *            the result list
	 * @return the report CSV
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static String getReportCSV(Map<String, String> experimentDataMap, Map<String, String> subjectMap,
			List<Map<String, Object>> resultList, String objectType) throws IOException {
		final StringWriter sw = new StringWriter();
		final CSVWriter csvWriter = new CSVWriter(sw, ',');
		csvWriter.writeAll(processRemoteData(experimentDataMap, subjectMap, resultList,objectType));
		csvWriter.close();
		sw.close();
		return sw.toString();
	}

	/**
	 * Process remote data.
	 *
	 * @param experimentDataMap
	 *            the experiment data map
	 * @param subjectMap
	 *            the subject map
	 * @param resultList
	 *            the result list
	 * @return the list
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private static List<String[]> processRemoteData(Map<String, String> experimentDataMap,
			Map<String, String> subjectMap, List<Map<String, Object>> resultList,String objectType) throws IOException {
		final Set<String[]> rptTableList = new HashSet<>();		
		if (resultList != null && !resultList.isEmpty()) {
			for (Iterator<Map<String, Object>> resultIter = resultList.iterator(); resultIter.hasNext();) {
				Map<String, Object> resultDataMap = resultIter.next();
				String data[]=new String[2];
				for (Iterator<String> iterator = resultDataMap.keySet().iterator(); iterator.hasNext();) 
				{
					String key = iterator.next();
					String value = (String) resultDataMap.get(key);
					
					if ("label".equalsIgnoreCase(key))
						data[0]=value;
					else if ("remote_xnat_id".equals(key)) 
					{
						if("Subject".equalsIgnoreCase(objectType))
							data[1]=subjectMap.get(value);
						else
							data[1]=experimentDataMap.get(value);
					}
				}
				rptTableList.add(data);
			}
		}
		List<String[]> result=new ArrayList<String[]>();
		result.add(new String[] { "Local_Label", "Remote_Label" });
		result.addAll(rptTableList);
		return result;
	}

	/**
	 * Gets the report JSON.
	 *
	 * @param experimentDataMap
	 *            the experiment data map
	 * @param subjectMap
	 *            the subject map
	 * @param resultList
	 *            the result list
	 * @return the report JSON
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static String getReportJSON(Map<String, String> experimentDataMap, Map<String, String> subjectMap,
			List<Map<String, Object>> resultList,String objectType) throws IOException {
		final Gson gson = new Gson();
		return gson.toJson(processRemoteData(experimentDataMap, subjectMap, resultList,objectType));
	}

	/**
	 * Gets the result.
	 *
	 * @param uri
	 *            the uri
	 * @return the result
	 * @throws Exception
	 *             the exception
	 */
	private RemoteConnectionResponse getResult(String uri) throws Exception {
		try {
			RemoteConnectionHandler remoteConnectionHandler = new RemoteConnectionHandler(_jdbcTemplate,
					_queryResultUtil);
			RemoteConnection connection = remoteConnectionHandler.getConnection(
					projectSyncConfiguration.getProject().getId(),
					projectSyncConfiguration.getProjectSyncConfigurationFromDB().getSyncinfo().getRemoteUrl());
			RemoteConnectionResponse response = _manager.getResult(connection, uri);
			return response;
		} catch (Exception e) {
			System.out.println("Error " + ExceptionUtils.getStackTrace(e));
			_log.debug(e.getMessage());
			throw e;
		}
	}
}
