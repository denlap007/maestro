/*
 * Copyright (C) 2015-2016 Dionysis Lappas <dio@freelabs.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.freelabs.maestro.core.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to archive/un-archive files.
 */
public class Archiver {

    /**
     * Size of buffer when reading files.
     */
    private static final int READ_BUFFER_SIZE = 1024;
    /**
     * Size of buffer when writing files.
     */
    private static final int WRITE_BUFFER_SIZE = 4096;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Archiver.class);

    /**
     * Unzips a zipped file to a destination folder.
     *
     * @param destinationFolder the folder where to unzip the zipped file.
     * @param zipFile the zipped file path.
     */
    public void unzip(String destinationFolder, String zipFile) throws IOException {
        List<File> dirs = new ArrayList<>();
        File directory = new File(destinationFolder);

        // if the output directory doesn't exist, create it
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // buffer for read and write data to file
        byte[] buffer = new byte[WRITE_BUFFER_SIZE];

        try (FileInputStream fInput = new FileInputStream(zipFile);
                ZipInputStream zipInput = new ZipInputStream(fInput)) {

            ZipEntry zipEntry = zipInput.getNextEntry();
            
            while (zipEntry != null) {
                String entryName = zipEntry.getName();
                File file = new File(destinationFolder + File.separator + entryName);
                // create the directories of the zip directory
                if (zipEntry.isDirectory()) {
                    File newDir = new File(file.getAbsolutePath());
                    if (!newDir.exists()) {
                        boolean success = newDir.mkdirs();
                        dirs.add(newDir);
                        if (success == false) {
                            LOG.error("Problem creating Folder.");
                        }
                    }
                } else {
                    try (FileOutputStream fOutput = new FileOutputStream(file)) {
                        int count;
                        while ((count = zipInput.read(buffer)) > 0) {
                            // write 'count' bytes to the file output stream
                            fOutput.write(buffer, 0, count);
                        }
                    }
                    // set file permissions
                    file.setExecutable(true);
                    file.setReadable(true);
                    file.setWritable(true);
                }
                // close ZipEntry and take the next one
                zipInput.closeEntry();
                zipEntry = zipInput.getNextEntry();
            }

            // close the last ZipEntry
            zipInput.closeEntry();
        }
    }

    /**
     * Zips a file applying best compression possible.
     *
     * @param inputFolder the folder/file to zip.
     * @param targetZippedFolder the path of the zipped file.
     * @throws IOException in case of I/O error during read/write file I/O.
     */
    public void zip(String inputFolder, String targetZippedFolder) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(targetZippedFolder);

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            // set attributes
            zipOutputStream.setLevel(Deflater.BEST_COMPRESSION);
            zipOutputStream.setMethod(ZipOutputStream.DEFLATED);

            File inputFile = new File(inputFolder);

            if (inputFile.isFile()) {
                zipFile(inputFile, "", zipOutputStream);
            } else if (inputFile.isDirectory()) {
                // list all files/folders
                File[] allFiles = inputFile.listFiles();

                for (File file : allFiles) {
                    if (file.isFile()) {
                        zipFile(file, "", zipOutputStream);
                    } else {
                        zipFolder(zipOutputStream, file, "");
                    }
                }
            }
        }
    }

    /**
     * Zips a folder.
     *
     * @param zipOutputStream a wrapped stream for zipping.
     * @param inputFolder the folder to zip.
     * @param parentName name of the parent folder.
     * @throws IOException in case of I/O error during read/write file I/O.
     */
    private void zipFolder(ZipOutputStream zipOutputStream, File inputFolder, String parentName) throws IOException {
        String myname = parentName + inputFolder.getName() + File.separator;

        ZipEntry folderZipEntry = new ZipEntry(myname);
        zipOutputStream.putNextEntry(folderZipEntry);

        File[] contents = inputFolder.listFiles();

        for (File f : contents) {
            if (f.isFile()) {
                zipFile(f, myname, zipOutputStream);
            } else if (f.isDirectory()) {
                zipFolder(zipOutputStream, f, myname);
            }
        }
        zipOutputStream.closeEntry();
    }

    /**
     * Zips a file
     *
     * @param inputFile path of file to zip.
     * @param parentName name of parent folder
     * @param zipOutputStream a wrapped stream for zipping.
     * @throws IOException in case of I/O error during read/write file I/O.
     */
    private void zipFile(File inputFile, String parentName, ZipOutputStream zipOutputStream) throws IOException {
        // A ZipEntry represents a file entry in the zip archive
        // We name the ZipEntry after the original file's name
        ZipEntry zipEntry = new ZipEntry(parentName + inputFile.getName());
        zipEntry.setMethod(ZipEntry.DEFLATED);

        zipOutputStream.putNextEntry(zipEntry);

        FileInputStream fileInputStream = new FileInputStream(inputFile);
        byte[] buf = new byte[READ_BUFFER_SIZE];
        int bytesRead;

        // Read the input file by chucks of 1024 bytes
        // and write the read bytes to the zip stream
        while ((bytesRead = fileInputStream.read(buf)) > 0) {
            zipOutputStream.write(buf, 0, bytesRead);
        }

        // close ZipEntry to store the stream to the file
        zipOutputStream.closeEntry();
    }

    /**
     * Generates hash checksum using SHA-256 algorithm.
     *
     * @param inputFile the file to compute hash.
     * @return the hash computed by SHA-256 algorithm.
     * @throws IOException in case of I/O error.
     * @throws NoSuchAlgorithmException if SHA-256 algorithm is not found.
     */
    public String calcSHA256(String inputFile) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream fis = new FileInputStream(new File(inputFile));

        byte[] dataBytes = new byte[READ_BUFFER_SIZE];

        int nread;
        while ((nread = fis.read(dataBytes)) != -1) {
            md.update(dataBytes, 0, nread);
        }
        byte[] mdbytes = md.digest();

        //convert the byte to hex format
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < mdbytes.length; i++) {
            hexString.append(Integer.toHexString(0xFF & mdbytes[i]));
        }

        return hexString.toString();
    }

}
