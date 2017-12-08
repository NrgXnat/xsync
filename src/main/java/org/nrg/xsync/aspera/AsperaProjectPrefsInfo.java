package org.nrg.xsync.aspera;

public class AsperaProjectPrefsInfo {

	private Boolean asperaEnabled;
	private String asperaNodeUrl;
	private String asperaNodeUser;
	private String privateKey;
	private String destinationDirectory;
	private String logDirectory;
	private String sshPort;
	private String udpPort;
    
	public AsperaProjectPrefsInfo(AsperaProjectPrefs prefs, String entityId) {
		this.asperaEnabled = prefs.getAsperaEnabled(entityId);
		this.asperaNodeUrl = prefs.getAsperaNodeUrl(entityId);
		this.asperaNodeUser = prefs.getAsperaNodeUser(entityId);
		this.privateKey = prefs.getPrivateKey(entityId);
		this.destinationDirectory = prefs.getDestinationDirectory(entityId);
		this.logDirectory = prefs.getLogDirectory(entityId);
		this.sshPort = prefs.getSshPort(entityId);
		this.udpPort = prefs.getUdpPort(entityId);
    }

    public AsperaProjectPrefsInfo() {
    }

    public Boolean getAsperaEnabled() {
		return asperaEnabled;
	}

	public void setAsperaEnabled(Boolean asperaEnabled) {
		this.asperaEnabled = asperaEnabled;
	}

	public String getAsperaNodeUrl() {
		return asperaNodeUrl;
	}

	public void setAsperaNodeUrl(String asperaNodeUrl) {
		this.asperaNodeUrl = asperaNodeUrl;
	}

	public String getAsperaNodeUser() {
		return asperaNodeUser;
	}

	public void setAsperaNodeUser(String asperaNodeUser) {
		this.asperaNodeUser = asperaNodeUser;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public String getDestinationDirectory() {
		return destinationDirectory;
	}

	public void setDestinationDirectory(String destinationDirectory) {
		this.destinationDirectory = destinationDirectory;
	}

	public String getLogDirectory() {
		return logDirectory;
	}

	public void setLogDirectory(String logDirectory) {
		this.logDirectory = logDirectory;
	}

	public String getSshPort() {
		return sshPort;
	}

	public void setSshPort(String sshPort) {
		this.sshPort = sshPort;
	}

	public String getUdpPort() {
		return udpPort;
	}

	public void setUdpPort(String udpPort) {
		this.udpPort = udpPort;
	}
    	
}
