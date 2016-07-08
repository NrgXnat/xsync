# XSYNC plugin #

 XSYNC implemented as a XNAT 1.7 plugin.

This plugin enables scheduled syncing of data between a project on a XNAT host and another project on another XNAT host. The source XNAT host must be deployed using XNAT 1.7, the destination XNAT could be on version 1.6* but the data-types which are being synced must exist on both XNAT hosts.

Optionally, DICOM data can be anonymized before its sent over to the destination project. 


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

You can verify your completed build by looking in the folder **build/libs**. It should contain a file named something like **xsync-plugin-0.1-SNAPSHOT.jar**. This is the plugin jar that you can install in your XNAT's **plugins** folder.

## Installing the plugin ##

Installing the plugin is as simple as stopping the Tomcat server running your XNAT 1.7 application, copying the plugin jar into your **plugins** folder, and restarting Tomcat. To verify that the data type from the sample plugin installed correctly:

## Setting up XSync for a project ##

In order to sync data between a project, say SOURCE, on a XNAT instance, say XNAT_SOURCE, to a project, say DESTINATION, on a XNAT instance, say XNAT_DESTINATION, perform the following steps

STEP 1:

Deploy Xsync Plugin on XNAT_SOURCE host.

STEP 2:

Create a project SOURCE on XNAT_SOURCE

STEP 3:

Create a project DESTINATION on XNAT_DESTINATION

STEP 4:

Create a copy of the file xsync_plugin\src\main\resources\sync_config.json. 

Modify the following fields:

remote_url : set this to the URL of XNAT_DESTINATION host

STEP 5 (temporary until XSync Setup UI is available):

Login to XNAT_SOURCE as admin user, navigate to Administer -> Site Administration -> Other -> Miscellaneous -> Swagger.

Click on xsync-setup-controller

Click on POST /xapi/xsync/projects/{projectId}

Set the projectId to SOURCE

Set the jsonbody to the JSON text from the modified sync_config.json file

Submit using the "Try it out!" button. 

STEP 6 - OPTIONAL (temporary until XSync Setup UI is available):

In case pre-sync DICOM anonymization is required, a sample DICOM anonymization script is available at xsync_plugin\src\main\resources\anon.das. Create a copy of this file to set the DICOM anonymization rules for project SOURCE.

Login to XNAT_SOURCE as admin user, navigate to Administer -> Site Administration -> Other -> Miscellaneous -> Swagger.

Click on xsync-setup-controller

Click on PUT /xapi/xsync/projects/{projectId}/presyncanonymization

Set the projectId to SOURCE

Set the anonymizationScript to the  text from the modified anon.das file

Submit using the "Try it out!" button. 

STEP 7: 

Navigate to SOURCE project report, under the Manage tab, select XSync Credentials to enter the XNAT_DESTINATION host login credentials. 

STEP 8: 

Depending on the XSync configuration, sync operation will be performed either Daily at 00.00 hours, Weekly (Every Saturday at 01.00 hours) or Monthly (1st of the month at 02.00 hours).

One could trigger the sync operation using the Sync Data link on the SOURCE project report Action Box.

STEP 9:

Once the sync operation completes, it sends out an email and also, creates a HTML file on the XNAT_CACHE_FOLDER/SYNCHRONIZATION/PROJECT_NAME/SYNC_START_TIMESTAMP folder.

## More about the Xsync configuration JSON ##

XSync is uses the fields in the JSON to decide what and when to sync over to the remote site. 

The field, sync_new_only, decides at sync time which of the SOURCE project entities will be synced. 

If sync_new_only is set to true, this implies that only those entities which are new since last sync end-time will be synced. Any entity which has been deleted or updated will be ignored. 

One can define which of the project level resources are to be synced. This is done using the field:

project_resources

One can define which of the subject level resources are to be synced. This is done using the field:

subject_resources

One can define which of the subject assessors (which are not imaging sessions) are to be synced. This is done using the field:

subject_assessors

One can define which of the imaging sessions, their resources, their scans, their scan resources and their assessors are to be synced. This is done using the field:

imaging_sessions

The fiels sync_type can have one of the four values viz., all, none, include, exclude

For example, if scan_types field for xnat:mrSessionData is set to:

	{
          "sync_type": "include",
          "scan_type_list": ["T1w", "T2w"]
        },
        
this implies that ONLY scan types T1w and T2w will be synced. All other scan-types will be ignored. If an imaging session has no such scan type, then only the session metadata will be synced. On the other hand, 

	{
          "sync_type": "exclude",
          "scan_type_list": ["T1w", "T2w"]
        },

implies that ALL scans except T1w and T2w are to be synced. 


The subfield, needs_ok_to_sync, of advanced_options, is to be used for situations when one wants a manual check before an imaging session is synced. If set to true, all imaging session will be ignored unless they are marked Ok to Sync. 

To mark an imaging session Ok to Sync, from the imaging session report page, use the Synchronization tab. 
