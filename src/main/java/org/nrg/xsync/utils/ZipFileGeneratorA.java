package org.nrg.xsync.utils;

import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.nrg.xnat.restlet.representations.ZipRepresentation;
import org.nrg.xnat.utils.CatalogUtils;
import org.nrg.xsync.manager.SynchronizationManager;
import org.restlet.data.MediaType;

import java.io.*;
import java.util.*;

public abstract class ZipFileGeneratorA {

    private final long maxZipSize;
    final int zipCompression = 0;

    public ZipFileGeneratorA(final long mSize) {
        maxZipSize = mSize;
    }

    public List<File> zip(final String remoteProjectId,  final File pathToFiles) throws IOException {
        List<File> zipFiles = new ArrayList<>();
        final String zipRoot = new Long(new Date().getTime()).toString();

        String expCachePath = SynchronizationManager.GET_SYNC_FILE_PATH(remoteProjectId);
        new File(expCachePath).mkdirs();
        String path = pathToFiles.getAbsolutePath();

        if (pathToFiles.exists()) {
            List<File> filteredFiles = new ArrayList<>(org.apache.commons.io.FileUtils.listFiles(pathToFiles,
                    CatalogUtils.XNAT_CATALOGABLE_FILE_FILTER, DirectoryFileFilter.DIRECTORY));

            if (!filteredFiles.isEmpty()) {
                Collections.sort(filteredFiles, Collections.reverseOrder(Comparator.comparing(File::length)));
                ZipRepresentation zipRepresentation = new ZipRepresentation(MediaType.APPLICATION_ZIP, pathToFiles.getParent(), zipCompression);
                List<File> filesInCurrentZip = new ArrayList<>();
                if (maxZipSize == -1) { //Dont split in parts
                    return buildZip(filteredFiles, expCachePath, pathToFiles.getParent(), path);
                } else {
                    int count = 0;
                    long currentZipSize = 0;
                    long nextFileSize = -1;
                    for (File currentFile : filteredFiles) {
                        nextFileSize = currentFile.length();
                        if (currentZipSize + nextFileSize >= maxZipSize) {
                            zipRepresentation.addAllAtRelativeDirectory(path, filesInCurrentZip);
                            File zipFile = new File(expCachePath, (zipRoot + (".part." + count++ + ".zip")));
                            zipFile.deleteOnExit();
                            zipRepresentation.write(new FileOutputStream(zipFile));
                            zipFiles.add(zipFile);
                            filesInCurrentZip = new ArrayList<>();
                            filesInCurrentZip.add(currentFile);
                            currentZipSize = nextFileSize;
                            zipRepresentation = new ZipRepresentation(MediaType.APPLICATION_ZIP, pathToFiles.getParent(), zipCompression);
                        } else {
                            filesInCurrentZip.add(currentFile);
                            currentZipSize += nextFileSize;
                        }
                    }
                    if (!filesInCurrentZip.isEmpty()) { //All files add up to less than the maxZipSize
                        zipFiles.addAll(buildZip(filesInCurrentZip, expCachePath, pathToFiles.getParent(), path));
                    }
                }
            }
        }
        return zipFiles;
    }

    private List<File> buildZip(final List<File> files, final String expCachePath, final String parentPath, final String parentAbsolutePath) throws IOException, FileNotFoundException{
        List<File> zipFiles = new ArrayList<>();
        if (files.isEmpty()) {
            return zipFiles;
        }
        ZipRepresentation zipRepresentation = new ZipRepresentation(MediaType.APPLICATION_ZIP, parentPath, zipCompression);
        zipRepresentation.addAllAtRelativeDirectory(parentAbsolutePath, files);
        File zipFile = new File(expCachePath, (new Date()).getTime()+".zip");
        zipFile.deleteOnExit();
        zipRepresentation.write(new FileOutputStream(zipFile));
        zipFiles.add(zipFile);
        return zipFiles;
    }
}
