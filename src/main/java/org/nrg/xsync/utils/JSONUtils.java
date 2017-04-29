package org.nrg.xsync.utils;

import java.io.IOException;

import org.nrg.framework.services.SerializerService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mohana Ramaratnam
 *
 */
public class JSONUtils {
	
	private final SerializerService _serializer;
	
	public JSONUtils(SerializerService serializer) {
		_serializer = serializer;
	}
	
	//Parses a JSON resultset to extract the name and uri of files as a MAP
	public JsonNode toJSONNode(String jsonStr) {
	  JsonNode json = null;
		try {
			json = _serializer.deserializeJson(jsonStr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return json;
	}

	
}


