package org.nrg.xsync.discoverer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.nrg.xsync.configuration.json.SyncConfiguration;
import org.nrg.xsync.utils.QueryResultUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Mohana Ramaratnam
 *
 */
public class TestJson {
	
	public static void main(String[] args) {
		TestJson test = new TestJson();
		//test.testCollation();
		test.loadConfiguration();
		System.out.println("Done");
		
	}
	
	private void loadConfiguration() {
		String filePath = "C:\\Junk\\sync_configuration.json";
		ObjectMapper objectMapper = new ObjectMapper();
		try {
		SyncConfiguration syncConfiguration = objectMapper.readValue(new File(filePath), SyncConfiguration.class);
		System.out.println(syncConfiguration);
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void testMapToJson(List<Map<String,Object>> mylist) {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode json = null;
		ObjectNode root = null;
		try {
			String jsonAsStr = objectMapper.writeValueAsString(mylist);
			root = objectMapper.createObjectNode();
			json = objectMapper.readTree(jsonAsStr);
			root.put("subjectassessors", json);
			System.out.println(root.toString());
		}catch(Exception e) {
			e.printStackTrace();
		}
	
	}
	
	private void testMapToJson() {
		List<Map<String,String>> mylist = new ArrayList();
		Map<String,String> m = new HashMap<String,String>();
		m.put("xsiType","dpuk:assessmentData");
		m.put("singularName","DPUK Assessment Data");

		Map<String,String> m2 = new HashMap<String,String>();
		m2.put("xsiType","dpuk:riskFactorAssessmentData");
		m2.put("singularName","DPUK RISK Assessment Data");

		
		mylist.add(m);
		mylist.add(m2);
		//testMapToJson(mylist);
		System.out.println("DONE");
		
	}
	
	public void testCollation() {
		/*
		 "type";"xsitype";"singularname"
		 "MEGTypeScan1";"xnat:megSessionData";"MEGSession"
		"3D FLAIR";"xnat:mrSessionData";"MRSession"
		"3D T1";"xnat:mrSessionData";"MRSession"
		"3D T2";"xnat:mrSessionData";"MRSession"
		"BLA";"xnat:mrSessionData";"MRSession"
		"BLA1";"xnat:mrSessionData";"MRSession"
		"Resting StatefMRI";"xnat:mrSessionData";"MRSession"
		"AC";"xnat:petSessionData";"PETSession"
		"FC";"xnat:petSessionData";"PETSession"
		"Map";"xnat:petSessionData";"PETSession"
		"NAC";"xnat:petSessionData";"PETSession"
		*/
		List<Map<String,Object>> mylist = new ArrayList<Map<String,Object>>();
		Map<String,Object> m = new HashMap<String,Object>();
		m.put("type","MEGTypeScan1");
		m.put("xsitype","xnat:megSessionData");
		m.put("singularname", "MEGSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","3D FLAIR");
		m.put("xsitype","xnat:mrSessionData");
		m.put("singularname", "MRSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","3D T1");
		m.put("xsitype","xnat:mrSessionData");
		m.put("singularname", "MRSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","3D T2");
		m.put("xsitype","xnat:mrSessionData");
		m.put("singularname", "MRSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","BLA");
		m.put("xsitype","xnat:mrSessionData");
		m.put("singularname", "MRSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","BLA1");
		m.put("xsitype","xnat:mrSessionData");
		m.put("singularname", "MRSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","Resting StatefMRI");
		m.put("xsitype","xnat:mrSessionData");
		m.put("singularname", "MRSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","AC");
		m.put("xsitype","xnat:petSessionData");
		m.put("singularname", "PETSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","NAC");
		m.put("xsitype","xnat:petSessionData");
		m.put("singularname", "PETSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","FC");
		m.put("xsitype","xnat:petSessionData");
		m.put("singularname", "PETSession");
		mylist.add(m);
		m = new HashMap<String,Object>();
		m.put("type","Map");
		m.put("xsitype","xnat:petSessionData");
		m.put("singularname", "PETSession");
		mylist.add(m);
		
		QueryResultUtil queryResultUtil = new QueryResultUtil();
		List<Map<String,Object>> reOrganized = queryResultUtil.reorganizeAsPivotColumnArray(mylist, "xsitype", "type");
		for (Map<String,Object> arow : reOrganized) {
			for (Entry<String,Object> r : arow.entrySet()) {
				System.out.println(r.getKey() + "=" + r.getValue());
			}
		}
		testMapToJson(reOrganized);
	}

}
