package org.nrg.xsync.utils;

import java.util.List;

public class ScriptResult {
	
   	private boolean status; 
	private Integer returnCode; 
	private List<String> resultList;
	
	public ScriptResult() {
	}
	
	public ScriptResult(boolean status, Integer returnCode, List<String> resultList) {
		setStatus(status);
		setReturnCode(returnCode);
		setResultList(resultList);
	}
	
	public void setStatus(boolean status) {
		this.status = status;
	}
	
	public void setReturnCode(Integer returnCode) {
		this.returnCode = returnCode;
	}
	
	public void setResultList(List<String> ResultList) {
		this.resultList = ResultList;
	}
	
	public boolean getStatus() {
		return status;
	}
	
	public Integer getReturnCode() {
		return returnCode;
	}
	
	public List<String> getResultList() {
		return resultList;
	}
	
}
