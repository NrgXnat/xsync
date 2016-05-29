# XSYNC plugin #

 XSYNC implemented as an XNAT 1.7 plugin.

# Building #

To build the plugin, run the following command from within the plugin folder:

```bash
./gradlew jar
```

On Windows, you may need to run:

```bash
gradlew jar
```

If you haven't previously run this build, it may take a while for all of the dependencies to download.

You can verify your completed build by looking in the folder **build/libs**. It should contain a file named something like **xsync-plugin-0.1-SNAPSHOT.jar**. This is the plugin jar that you can install in your XNAT's **plugins** folder.

## Installing ##

Installing the plugin is as simple as stopping the Tomcat server running your XNAT 1.7 application, copying the plugin jar into your **plugins** folder, and restarting Tomcat. To verify that the data type from the sample plugin installed correctly:

# Log into your XNAT as an administrator once it's completed the start-up process.
# Click the menu command **Administer->Data Types**.
# Look in the list of available data types for **xsync:xsyncData**.

