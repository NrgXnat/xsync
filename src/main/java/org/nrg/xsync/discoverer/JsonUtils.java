package org.nrg.xsync.discoverer;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Mohana Ramaratnam
 *
 */
public class JsonUtils {
    private static final Logger _log = LoggerFactory.getLogger(JsonUtils.class);
	
	public JsonNode  toJSONFromMap(List<Map<String,Object>> results) {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode json = null;
		try {
			String jsonAsStr = objectMapper.writeValueAsString(results);
			json = objectMapper.readTree(jsonAsStr);
		}catch(Exception e) {
			_log.error("Failed to parse the results " + e.getMessage());
		}
		return json;
	}

	public JsonNode  toJSONFromList(List<String> results) {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode json = null;
		try {
			String jsonAsStr = objectMapper.writeValueAsString(results);
			json = objectMapper.readTree(jsonAsStr);
		}catch(Exception e) {
			_log.error("Failed to parse the results " + e.getMessage());
		}
		return json;
	}
	
	public void debug(String name, List<Map<String, Object>> list) {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode json;
		ObjectNode root;
		try {
			String jsonAsStr = objectMapper.writeValueAsString(list);
			root = objectMapper.createObjectNode();
			json = objectMapper.readTree(jsonAsStr);
			root.put(name, json);
			System.out.println(root.toString());
		}catch(Exception e) {
			e.printStackTrace();
		}
	

	}

}
