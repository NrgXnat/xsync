package org.nrg.xsync.aspera;

import java.util.ArrayList;
import java.util.Date;

public class AsperaStatus {

    // Keep a static array of all statuses
    public static ArrayList<AsperaStatus> list = new ArrayList<>();
    private String project;
    private String session;
    private String status;
    private String message;
    private String size;
    private Date timestamp;
    private Date elapsed;

//    private String percent;
//    private String currentFile;

    public AsperaStatus() {
        this.timestamp = new Date();
        this.status = "idle";
    }

//    public AsperaStatus(String project, String status) {
//        this.timestamp = new Date();
//        this.project = project;
//        this.status = status;
////        list.add(this);
//    }
//
//    public AsperaStatus(String project, String session, String status) {
//        this.timestamp = new Date();
//        this.project = project;
//        this.session = session;
//        this.status = status;
////        list.add(this);
//    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void updateTimestamp() {
        this.timestamp = new Date();
    }
}
