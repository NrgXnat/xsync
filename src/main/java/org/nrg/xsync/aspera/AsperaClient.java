package org.nrg.xsync.aspera;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

@Component
public class AsperaClient {

    @Autowired
    public AsperaClient(AsperaProjectPrefs prefs) {
        super();
        _prefs = prefs;
        // Create a status for each client instance
        status = new AsperaStatus();
        AsperaStatus.list.add(status);
    }

    public void testConnection() {}
    
    public boolean upload(String projectID, File xarFile) throws IOException, InterruptedException {

        // TODO pull archive path from site config
        this.status.setSize(String.format("%s GB", 1));

        final String[] command = {
                "/usr/local/bin/ascp", "-v", "-l", "10G",
                "-P", _prefs.getSshPort(projectID),
                "-i", _prefs.getPrivateKey(projectID),
                "-L", _prefs.getLogDirectory(projectID),
                xarFile.getCanonicalPath(),
                String.format("%s@%s:%s",
                        _prefs.getAsperaNodeUser(projectID),
                        _prefs.getAsperaNodeUrl(projectID),
                        _prefs.getDestinationDirectory(projectID)
                )
        };

        final String commandStr = StringUtils.join(command, " ");
//        _logger.info(Arrays.toString(command));
        _logger.debug("Executing system command -- " + commandStr);
        this.status.setMessage(commandStr);

        final Runtime rt = Runtime.getRuntime();
        try {
            this.status.setStatus("running");
            proc = rt.exec(command);
        } catch (Exception e) {
            this.status.setStatus("error");
            this.status.setMessage("Failed to execute command -- " + commandStr);
        }

        final BufferedReader stdInput = (proc != null && proc.getInputStream() != null) ? 
        		new BufferedReader(new InputStreamReader(proc.getInputStream())) : null;

        final BufferedReader stdError = (proc != null && proc.getErrorStream() != null) ? 
        		new BufferedReader(new InputStreamReader(proc.getErrorStream())) : null;

        String s;
        if (stdInput != null) {
	        while ((s = stdInput.readLine()) != null) {
	        	//_logger.info(s);
	            _logger.debug(s);
	            this.status.setMessage(s);
	        }
            stdInput.close();
        }

        proc.waitFor();

        this.status.setStatus("complete");

        if (proc.exitValue() != 0) {
            _logger.error("ERROR: ascp command exited with status " + proc.exitValue());
            this.status.setStatus("error");

            if (stdError != null) {
	            while ((s = stdError.readLine()) != null) {
	                _logger.error(s);
	                this.status.setMessage(s);
	            }
	            stdError.close();
            }
            return false;
        }
        if (stdError != null) {
            stdError.close();
        }
        return true;
    }

    public boolean upload(String projectId, String sessionLabel) throws IOException, InterruptedException {
        this.status.setProject(projectId);
        this.status.setSession(sessionLabel);

        // TODO pull archive path from site config
        final String sessionDirectory = String.format("/data/intradb/archive/%s/arc001/%s", projectId, sessionLabel);

        final File d = new File(sessionDirectory);
        if (! d.exists()) {
            this.status.setMessage(d + " does not exist on local XNAT server");
            this.status.setStatus("error");
            return false;
        }
        float folderSize = folderSize(d);
        this.status.setSize(String.format("%s GB", folderSize));

        String[] command = {
                "/usr/local/bin/ascp", "-v", "-l", "10G",
                "-P", _prefs.getSshPort(projectId),
                "-i", _prefs.getPrivateKey(projectId),
                "-L", _prefs.getLogDirectory(projectId),
                sessionDirectory,
                String.format("%s@%s:%s",
                        _prefs.getAsperaNodeUser(projectId),
                        _prefs.getAsperaNodeUrl(projectId),
                        _prefs.getDestinationDirectory(projectId)
                )
        };

        String commandStr = StringUtils.join(command, " ");
//        _logger.info(Arrays.toString(command));
        _logger.debug("Executing system command -- " + commandStr);
        this.status.setMessage(commandStr);

        Runtime rt = Runtime.getRuntime();
        try {
            this.status.setStatus("running");
            proc = rt.exec(command);
        } catch (Exception e) {
            this.status.setStatus("error");
            this.status.setMessage("Failed to execute command -- " + commandStr);
        }

        final BufferedReader stdInput = (proc != null && proc.getInputStream() != null) ? 
        		new BufferedReader(new InputStreamReader(proc.getInputStream())) : null;

        final BufferedReader stdError = (proc != null && proc.getErrorStream() != null) ? 
        		new BufferedReader(new InputStreamReader(proc.getErrorStream())) : null;

        String s;
        if (stdInput != null) {
	        while ((s = stdInput.readLine()) != null) {
	        	//_logger.info(s);
	            _logger.debug(s);
	            this.status.setMessage(s);
	        }
            stdInput.close();
        }

        proc.waitFor();

        this.status.setStatus("complete");

        if (proc.exitValue() != 0) {
            _logger.error("ERROR: ascp command exited with status " + proc.exitValue());
            this.status.setStatus("error");

            if (stdError != null) {
	            while ((s = stdError.readLine()) != null) {
	                _logger.error(s);
	                this.status.setMessage(s);
	            }
	            stdError.close();
            }
            return false;
        }
        if (stdError != null) {
            stdError.close();
        }
        return true;

    }

    public boolean isRunning(String project, String session) {
        for (int i = 0; i < AsperaStatus.list.size(); i++) {
            final AsperaStatus s = AsperaStatus.list.get(i);

            if (project.equals(s.getProject()) &&
                    session.equals(s.getSession()) &&
                    "running".equals(s.getStatus())) {
                return true;
            }
        }
        return false;
    }

    public boolean isRunning(String project) {
        for (int i = 0; i < AsperaStatus.list.size(); i++) {
            final AsperaStatus s = AsperaStatus.list.get(i);

            if (project.equals(s.getProject()) && "running".equals(s.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private float folderSize(File directory) {
        // Returns directory size in GB
        float length = 0;

        try {
            directory.listFiles();
        } catch (NullPointerException e) {
            _logger.error(String.valueOf(e));
            return 0;
        }

        for (File file : directory.listFiles()) {
            if (file.isFile())
                length += file.length();
            else
                length += folderSize(file);
        }
        return length / 1024 / 1024 / 1024;
    }


    private static final Logger _logger = LoggerFactory.getLogger(AsperaClient.class);
    private static AsperaProjectPrefs _prefs;
    private Process proc;
    public AsperaStatus status;
}
