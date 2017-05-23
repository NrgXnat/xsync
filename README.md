# XSYNC plugin #

 XSYNC implemented as a XNAT 1.7 plugin.

Xsync plugin enables automatic synchronization of data from a project in one XNAT system to a project in a second system. Xsync is configurable to ensure that only the desired data is delivered, and if required, data is properly de-identified, and that it is delivered on a pre-set schedule. 

# Download #

[ Xsync Jar (Version 1.3-SNAPSHOT) ![Download](https://api.bintray.com/packages/nrgxnat/xnat-plugins/XSync/images/download.svg) ](https://bitbucket.org/hodgem/xsync_xnatdev/downloads/xsync-plugin-1.3-SNAPSHOT.jar)

[ Xsync Bean Jar (Version 1.3-SNAPSHOT) ![Download](https://api.bintray.com/packages/nrgxnat/xnat-plugins/XSync/images/download.svg) ](https://bitbucket.org/hodgem/xsync_xnatdev/downloads/xsync-plugin-beans-1.3-SNAPSHOT.jar)

# ChangeLog #

Version 1.3-SNAPSHOT As of May 23, 2017

Revised sync blocking behavior with a service and UI for reporting on current sync status. 

Fix for issue where sessions were sometimes sent without files.

Fix for issue where sessions can fail to sync if they are arriving when a sync is kicked off.

Fix for issue where data can be overwritten if configured URL is modified to point to a shadow server.

Version 1.2 May 3, 2017

Xsync Token Refresh Issue fixed

Xsync File Transfer verification added. This results in new Sync statuses, viz., 

SYNCED_AND_NOT_VERIFIED
SYNCED_AND_VERIFIED
INCOMPLETE

Ability to sync a single experiment out of sync cycle.

# Building #

To build the plugin, run the following command from within the plugin folder:

```bash
./gradlew clean jar
```

On Windows, you may need to run:

```bash
gradlew clean jar
```

If you haven't previously run this build, it may take a while for all of the dependencies to download.

You can verify your completed build by looking in the folder **build/libs**. It should contain  files named something like **xsync-plugin-*-1.0.jar**. This is the plugin jar that you can install in your XNAT's **plugins** folder.

# System Requirements #

Source and Destination XNAT must be running  XNAT version 1.7.2 or higher.

# Getting Started #

* Deploy Xsync Plugin on Source XNAT

 	 * Build Xsync Plugin jar or Use the Download link to download the two xsync jars, 
	
 	 * Stop Tomcat,
	
 	 * Copy the Xsync Plugin jars file into the folder <XNAT_HOME>/plugins,   

 	 * Restart Tomcat.

* Create a Project on the Destination XNAT

* Login to Source XNAT as a Source Project owner

 	 * Navigate to Manage Tab on the project report page.
	
 	 * From the Manage Tab, navigate to Xsync Configuration

 	 * Click on Begin Configuration

 	 * Enabled is selected by default. This option allows for sync operation to be carried out at scheduled time. 

    	 * New Data Only is selected by default. This option results in syncing of only data that is new since the last sync. Modification to data (update or delete) is not reflected on the destination project. 

         * Enter the URL of Destination XNAT in Destination XNAT (be sure you're specifying the protocol for the destination XNAT in the URL, that is, specify https://myxnat.org, not just myxnat.org)

         * Enter the Destination Project.

         * Select desired Sync frequency. The choices are:

    	     * Hourly - Sync starts at 30 minutes past the hour, every hour,

    	     * Daily - Sync starts at midnight,

    	     * Weekly - Sync starts on Saturdays at 1am,

    	     * Monthly - Sync starts on 1st of the Month at 2am,

    	     * On Demand - Sync can be manually started using Sync Data Action

         * Select Anonymize Images if you want DICOM files to be anonymized before they are synced.

         * Click Submit.

     	 * Enter login credentials for the Destination XNAT. (If the user on the Destination XNAT does not have owner level access to the Destination Project, deletion of data in the Source Project will not be reflected on the Destination Project).

         * Click Submit.

         * If Anonymize Images was selected, enter the desired XNAT Anonymization script. A sample script is here. 

     * At this stage, basic Xsync configuration is complete. Depending on when the sync is scheduled,  data in Source Project would be synced to the Destination Project. The basic Xsync configuration syncs all Imaging Sessions (all resources, all scan and their files and Imaging Assessors) and none of Project resources, Subject Assessors. One can restrict entities that get synced using Show Advanced Settings. (See Xsync Advanced Features).

* After the sync operation completes

  * An email is sent to the user who configured Xsync. The email contains details of data that was synced.

  * A Xsync history report is available in Manage Tab -> Xsync Configuration.  

  * Log file with sync details is saved as a Source Project resource file. These files are never synced.

# Xsync Advanced Features #

As of Xsync Version 1.0, Xsync configuration UI does not expose all the configuration properties. In order to use the advanced features, Source XNAT Administrators can use XAPI Webservices. The Xsync configuration behind the scenes, is a JSON. 

* XNAT Data Model

  XNAT organises its data into Projects, Subjects, Subject Assessors (eg Clinical Data), Imaging Sessions (eg MR data), Imaging Session Assessors (eg Freesurfer data).

  Each element in the XNAT data hierarchy can have resources (ie files).  One can have Project resources, Subject resources, Subject Assessor Resources, Imaging Session Resources and Imaging Session Assessor Resources. Each of these resources is identified by a label (aka resource label). 

  Each data-type in XNAT is identified internally by its xsiType. For example, MR Session data is identified as xnat:mrSessionData. 

* Xsync configurable elements

  * Project resources to sync (by resource labels)

  * Subject assessors to sync (by xsiTypes)

  * Subject assessor resources to sync (by resource labels)

  * Imaging Session to sync (by xsiTypes)
    
      * Should the Imaging Session be marked as Ok to Sync before its sent over to the Destination Project. This feature of Xsync is very useful to make sure that curated sessions are synced.
    
      * Imaging Session resources to sync (by resource labels)
    
      * Scans to sync (by scan-types)
    
      * Scan resources to sync (by resource labels)
    
      * Imaging Session Assessors to sync (by xsiTypes)
    
      * For each of the above choices, Xsync uses the property sync-type, whose possible values are include, exclude, all, none, to decide what to sync. If this property is not specified, sync-type defaults to all.

* Marking an Imaging Session OK to Sync

  When the _needs_ok_to_sync_ flag is set to true for an Imaging Session, the Imaging Session is synced only when someone marks the session as _Ok to Sync_. This feature is useful when QC is to be done to make sure only curated data is sent over to the destination. 

  If _needs_ok_to_sync_ flag is set to true for an Imaging session, say MR Session (xnat:mrSessionData), on the MR Session report page, Synchronization tab enables marking the session as _Ok To Sync_. If the MR Session is not flagged as _Ok to Sync_, the MR Session will be skipped. 
      
      POST to /xapi/xsync/experiments/{experimentId} for marking sessions in bulk as _Ok to Sync_.
 
# Xsync  XAPI Web-Services #

Source XNAT Administrators can access the Xsync XAPI Services via Administrator -> Site Administration. From the Site Administration page, navigate to Miscellaneous. Click on "View the Swagger Page".

XNAT web-services can be used from the Swagger page itself  or invoked in a script or invoked by tools like curl.

Among the xsync-*-controller listed, the two most required ones are:

  * xsync-operations-controller : XNAT XSync Operations API

     * POST  /xapi/xsync/experiments/{experimentId} : sets OK to Sync Status for the experiment 

     * GET    /xapi/xsync/progress/{projectId}       : While the sync is in progress, this URI returns the log file that is created by Xsync. 
 
     * POST /xapi/xsync/projects/{projectId}        : starts syncing the project 
   
     * POST /xapi/xsync/unblock/{projectId}         : Xsync blocks a project while a sync is in progress. For some reason, if the sync fails, the block may be left on.

  * xsync-setup-controller : XSync Management API

     * GET /xapi/xsync/setup/projects/{projectId}          : Returns the Xsync Configuration JSON

     * POST /xapi/xsync/setup/projects/{projectId}         : Sets the Xsync Configuration JSON.

# Xsync Site Level Configuration #
 
 Source XNAT Administartors can use Administrators -> Plugin Settings to

   * set the Sync Retry Interval and Maximum Retries in case Xsync encounters problems communicating with the Destination XNAT. These default to 2 Hours and 2 respectively. 

   * set the Token Refresh Interval. Xsync uses tokens from the Destination XNAT to communicate. These tokens are refreshed by default every 10 hours. 

# Other Tips #

The Destination XNAT must have all the data-types deployed that the Source is attempting to sync.

 	 
# Bugs #

Report bugs to bugs@xnat.org
