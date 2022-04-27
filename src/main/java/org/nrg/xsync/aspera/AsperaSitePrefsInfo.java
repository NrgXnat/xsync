package org.nrg.xsync.aspera;

public class AsperaSitePrefsInfo {
	
	private String asperaNodeUrl;
	private String asperaNodeUser;
	private String privateKey;
	private String destinationDirectory;
	private String logDirectory;
	private String sshPort;
	private String udpPort;
	
    public AsperaSitePrefsInfo() {
    }

    public AsperaSitePrefsInfo(AsperaSitePrefs sitePrefs) {
    	this.asperaNodeUrl = sitePrefs.getAsperaNodeUrl();
    	this.asperaNodeUser = sitePrefs.getAsperaNodeUser();
    	this.privateKey = sitePrefs.getPrivateKey();
    	this.destinationDirectory = sitePrefs.getDestinationDirectory();
    	this.logDirectory = sitePrefs.getLogDirectory();
    	this.sshPort = sitePrefs.getSshPort();
    	this.udpPort = sitePrefs.getUdpPort();
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
