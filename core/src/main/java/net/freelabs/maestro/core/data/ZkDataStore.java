/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.freelabs.maestro.core.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class ZkDataStore {

    /**
     * A zookeeper handle to make requests to zookeeper servers.
     */
    private final ZooKeeper zk;
    /**
     * Chunk size of 0.5MB to split files uploaded to zookeeper service.
     */
    private static final int CHUNK_SIZE = 512000;
    /**
     * Name of the zip file generated after archiving. Also, the name of the
     * created zip file after merging.
     */
    private static final String ARCHIVE_NAME = "_archive.zip";
    /**
     * Merges byte chunks into file and splits file into byte chunks.
     */
    private final MergerSplitter mergerSplitter;
    /**
     * Zips/Un-zips files.
     */
    private final Archiver archiver;
    /**
     * Handles data downloading from zookeeper service.
     */
    private final ZkDataDownloader zkDataDownloader;
    /**
     * Handlers data uploading to zookeeper service.
     */
    private final ZkDataUploader zkDataUploader;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ZkDataStore.class);

    /**
     * Constructor
     *
     * @param zk a zookeeper handle to make requests to zookeeper servers.
     */
    public ZkDataStore(ZooKeeper zk) {
        this.zk = zk;
        archiver = new Archiver();
        mergerSplitter = new MergerSplitter(CHUNK_SIZE);
        zkDataDownloader = new ZkDataDownloader();
        zkDataUploader = new ZkDataUploader();
    }

    public boolean downloadData(String zPath) {
        return zkDataDownloader.downloadData(zPath);
    }

    public boolean uploadData(String inputFile, String zPath, String downloadPath) {
        return zkDataUploader.uploadData(inputFile, zPath, downloadPath);
    }

    private final class ZkDataDownloader {

        private boolean downloadData(String zPath) {
            boolean success = false;
            // get nodes of specified zPath
            List<String> rootDataNodes = getChildren(zPath);

            if (rootDataNodes != null) {
                if (rootDataNodes.isEmpty()) {
                    LOG.info("No uploaded data to download from: {}.", zPath);
                    success = true;
                } else {
                    // for every retrieved node
                    for (String rootDataNode : rootDataNodes) {
                        success = false;
                        // get root node data
                        rootDataNode = zPath + "/" + rootDataNode;
                        LOG.info("Downloading data indicated by znode {}.", rootDataNode);
                        byte[] data = getData(rootDataNode);
                        // read data
                        if (data != null) {
                            InfoNode infoNode = new InfoNode(data);
                            // donload all byte chunks
                            List<byte[]> chunks = getDataChunks(rootDataNode, infoNode.numOfDataNodes);
                            if (chunks != null) {
                                // get sha256 hash
                                String downloadedSha256 = infoNode.zipSha256;
                                try {
                                    // merge byte chunks
                                    String mergedFilePath = merge(chunks, infoNode.downloadPath);
                                    // calculate file sha256 hash
                                    String computedSha256 = calcDownZipSha256(mergedFilePath);
                                    // compare hashes
                                    if (downloadedSha256.equals(computedSha256)) {
                                        // un-archive
                                        unzip(infoNode.downloadPath, mergedFilePath);
                                        success = true;
                                        // delete archive
                                        deleteFile(mergedFilePath);
                                        // delete zk data nodes
                                        deleteDataNodes(rootDataNode, infoNode.numOfDataNodes);
                                    } else {
                                        LOG.error("Hashes of uploaded and downloaded files do not match.");
                                    }
                                } catch (IOException | NoSuchAlgorithmException ex) {
                                    LOG.error("Something went wrong: {}", ex);
                                }
                            }
                        }

                        if (!success) {
                            LOG.error("FAILED to download data indicated by znode {}.", rootDataNode);
                            break;
                        } else {
                            LOG.info("Downloaded data indicated by zNode: {}.", rootDataNode);
                        }
                    }
                }
            }
            return success;
        }

        private void deleteDataNodes(String rootDataNode, int numOfDataNodes) {
            LOG.info("Deleting data nodes.");
            String childPath = rootDataNode;
            List<String> childPaths = new ArrayList<>();
            // create path for all child znodes
            for (int i = 0; i < numOfDataNodes; i++) {
                // create child znode path
                childPath = childPath + "/" + String.valueOf(i);
                childPaths.add(childPath);
            }
            // delete nodes starting from the end with no children
            for (int i = numOfDataNodes - 1; i >= 0; i--) {
                // get child path
                childPath = childPaths.get(i);
                // delete znode
                deleteNode(childPath, -1);
            }
            // delete root data node
            deleteNode(rootDataNode, -1);
        }

        private void unzip(String unzipPath, String zipPath) throws IOException {
            LOG.info("Unzipping file: {}.", zipPath);
            archiver.unzip(unzipPath, zipPath);
        }

        private String calcDownZipSha256(String filePath) throws NoSuchAlgorithmException, IOException {
            LOG.info("Calculating hash for file: {}.", filePath);
            return archiver.calcSHA256(filePath);
        }

        private String merge(List<byte[]> chunks, String downloadDir) throws IOException {
            // create the full disk path of the downloaded file
            String downFilePath = downloadDir + File.separator + ARCHIVE_NAME;
            LOG.info("Merging data chunks into file: {}.", downFilePath);
            // create dirs if necessary
            File file = new File(downloadDir);
            file.mkdirs();
            // merge data chunks into a file
            mergerSplitter.mergeFile(chunks, downFilePath);
            return downFilePath;
        }

        private List<byte[]> getDataChunks(String zPath, int numOfDataNodes) {
            LOG.info("Getting data chunks from zookeeper.");
            List<byte[]> chunks = new ArrayList<>();
            // initialize variable for znode parh creation
            String dataNodePath = zPath;

            for (int i = 0; i < numOfDataNodes; i++) {
                // create path for data znode
                dataNodePath = dataNodePath + "/" + String.valueOf(i);
                // get data from node
                byte[] chunk = getData(dataNodePath);
                // check data retrieval
                if (chunk != null) {
                    // save to list
                    chunks.add(chunk);
                } else {
                    LOG.error("FAILED to download byte chunk: {}.", zPath);
                    return null;
                }
            }
            return chunks;
        }

        private byte[] getData(String zPath) {
            byte[] data = null;
            while (true) {
                try {
                    data = zk.getData(zPath, false, new Stat());
                    LOG.debug("Downloaded data from zNode: {}.", zPath);
                    break;
                } catch (KeeperException.NoNodeException e) {
                    LOG.error("Znode does not exist: {}.", zPath);
                    break;
                } catch (KeeperException.ConnectionLossException e) {
                    LOG.warn("Connection loss was detected. Retrying...");
                } catch (KeeperException ex) {
                    LOG.error("Something went wrong: ", ex);
                    break;
                } catch (InterruptedException ex) {
                    // log the event
                    LOG.warn("Thread Interruped. Stopping.");
                    // set the interrupt status
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return data;
        }

        private List<String> getChildren(String zPath) {
            List<String> children = null;
            while (true) {
                try {
                    children = zk.getChildren(zPath, false);
                    break;
                } catch (InterruptedException ex) {
                    // log event
                    LOG.warn("Interrupted. Stopping.");
                    // set interupt flag
                    Thread.currentThread().interrupt();
                    break;
                } catch (KeeperException.ConnectionLossException ex) {
                    LOG.warn("Connection loss was detected! Retrying...");
                } catch (KeeperException.NoNodeException ex) {
                    LOG.error("Node does NOT exist: {}.", ex.getMessage());
                    break;
                } catch (KeeperException ex) {
                    LOG.error("Something went wrong", ex);
                    break;
                }
            }
            return children;
        }

        /**
         * Deletes the specified zNode. The zNode mustn't have any children.
         *
         * @param path the zNode to delete.
         * @param version the data version of the zNode.
         */
        private void deleteNode(String path, int version) {
            while (true) {
                try {
                    zk.delete(path, version);
                    LOG.debug("Deleted znode: {}.", path);
                    break;
                } catch (InterruptedException ex) {
                    // log event
                    LOG.warn("Interrupted. Stopping.");
                    // set interupt flag
                    Thread.currentThread().interrupt();
                    break;
                } catch (KeeperException.ConnectionLossException ex) {
                    LOG.warn("Connection loss was detected. Retrying...");
                } catch (KeeperException.NoNodeException ex) {
                    LOG.error("Znode does NOT exist {}.", path);
                    break;
                } catch (KeeperException ex) {
                    LOG.error("Something went wrong", ex);
                    break;
                }
            }
        }
    }

    private final class ZkDataUploader {

        /**
         * Indicates error in execution.
         */
        private boolean error;

        private boolean uploadData(String inputFile, String zPath, String downloadPath) {
            LOG.info("Uploading to zk: {}", inputFile);
            // reset global variable for errors
            error = false;
            boolean success = false;
            try {
                // archive data
                String archivePath = archive(inputFile);
                // calculate sha256 for zip
                String sha256 = calcSHA256(archivePath);
                // split archive into byte chunks
                List<byte[]> chunks = split(archivePath);
                // get number of nodes to be created
                int numOfDataNodes = chunks.size();
                // create head node for data nodes with metadata
                success = createInfoNode(zPath, sha256, downloadPath, numOfDataNodes);
                // check for errors
                if (success) {
                    // upload to zookeeper service
                    success = createDataNodes(chunks, zPath);
                }
                // delete created archive
                deleteFile(archivePath);
            } catch (IOException | NoSuchAlgorithmException ex) {
                LOG.error("Something went wrong: {}", ex);
            }
            if (success) {
                LOG.info("Uploaded {}.", inputFile);
            } else {
                LOG.error("FAILED to upload data {}.", inputFile);
            }
            return success;
        }

        private boolean createInfoNode(String zPath, String zipSha256, String downloadPath, int numOfDataNodes) {
            LOG.info("Creating meta-data znode: {}.", zPath);
            // create data that contains zipSha256, downloadDir and total numOfDataNodes
            byte[] data = createInfoNodeData(zipSha256, downloadPath, numOfDataNodes);
            // create zNode
            return createZkNodeSync(zPath, data);
        }

        /**
         * Creates data for the head zNode of data chain, containing metadata
         * about the data transfer.
         *
         * @param zipSha256 the hash of the zip file.
         * @param downloadDir the path where the uploaded data will be
         * downloaded.
         * @return a String containing the metadata.
         */
        private byte[] createInfoNodeData(String zipSha256, String downloadPath, int numOfDataNodes) {
            InfoNode infoNode = new InfoNode(zipSha256, downloadPath, numOfDataNodes);
            return infoNode.toByteArr();
        }

        /**
         * Calculates hash of file based on SHA-256 algorithm.
         *
         * @param file the file to calculate hash.
         * @return hash of file based on SHA-256 algorithm.
         * @throws IOException in case of I/O error.
         * @throws NoSuchAlgorithmException if SHA-256 algorithm cannot be
         * found.
         */
        private String calcSHA256(String file) throws IOException, NoSuchAlgorithmException {
            LOG.info("Calculating sha256 of: {}.", file);
            return archiver.calcSHA256(file);
        }

        /**
         * Zips files and returns the path of the created .zip file.
         *
         * @param inputFile the file/folder to zip.
         * @return the path of the new zipped file.
         * @throws IOException in case of I/O error during zip process.
         */
        private String archive(String inputFile) throws IOException {
            LOG.info("Archiving: {}.", inputFile);
            // create file path for generated .zip
            File file = new File(inputFile);
            String parent = file.getParent();
            String archivePath = parent + File.separator + generateId() + ARCHIVE_NAME;
            // apply archiving
            archiver.zip(inputFile, archivePath);
            // return zip path
            return archivePath;
        }

        /**
         * Generates a random 10-digit positive zero-padded id.
         *
         * @return the 8-digit positive zero-padded id.
         */
        private String generateId() {
            int min = 0;
            int max = 99999;
            int numId = min + (new Random().nextInt(max - min));
            String id = String.format("%05d", numId);
            return id;
        }

        /**
         * Splits file into chunks of bytes. Default size for byte chunk is 1MB.
         * The last byte chunk may be less than 1MB, depending on the file size.
         *
         * @param inputFile the file to split.
         * @return a list of byte chunks, byte arrays of 1MB.
         * @throws IOException in case of error during split processing.
         */
        private List<byte[]> split(String inputFile) throws IOException {
            LOG.info("Splitting file into chunks: {}.", inputFile);
            // split data
            return mergerSplitter.splitFile(inputFile);
        }

        /**
         * Creates zNodes to a zookeeper service at the specified path. Nodes
         * are created in a chained manner. Every new node is created as a child
         * of the previous one, trailing back to the starting znode specified.
         *
         * @param chunks list with byte chunks.
         * @param zPath the path of the root node for data to be stored in
         * zookeeper.
         */
        private boolean createDataNodes(List<byte[]> chunks, String zPath) {
            boolean success = false;
            String nodePath = zPath;
            LOG.info("Creating data znodes.");
            for (int i = 0; i < chunks.size(); i++) {
                // create data
                byte[] nodeData = chunks.get(i);
                // ceate path for znode
                nodePath = nodePath + "/" + String.valueOf(i);
                // create znode
                success = createZkNodeSync(nodePath, nodeData);
                // check if operation succeeded
                if (!success) {
                    break;
                }
            }
            return success;
        }

        /**
         * Creates a a persistent zNode using the synchronous API.
         *
         * @param zkPath the path of the zNode.
         * @param data the data of the zNode.
         *
         */
        private boolean createZkNodeSync(String zkPath, byte[] data) {
            String nodePath = null;
            while (!error) {
                try {
                    nodePath = zk.create(zkPath, data, OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    LOG.debug("Created zNode: {}.", nodePath);
                    break;
                } catch (KeeperException.NodeExistsException e) {
                    // node exists while shoudln't
                    LOG.error("Node exists: {}.", zkPath);
                    error();
                    break;
                } catch (KeeperException.ConnectionLossException e) {
                    LOG.warn("Connection loss was detected. Retrying...");
                } catch (KeeperException ex) {
                    LOG.error("Something went wrong: ", ex);
                    error();
                    break;
                } catch (InterruptedException ex) {
                    // log the event
                    LOG.warn("Thread Interruped. Stopping.");
                    // set the interrupt status
                    Thread.currentThread().interrupt();
                    error();
                    break;
                }
                // check if the node was created in case of ConnectionLoss
                boolean found = checkNZkodeSync(zkPath, data);
                // check if there were any errors and if the node was found
                if (found) {
                    break;
                }
            }
            return !error;
        }

        /**
         * Checks if a zNode is created as it was supposed to.
         *
         * @param zkPath the path of the zNode to check.
         * @param data the data of the zNode.
         */
        private boolean checkNZkodeSync(String zkPath, byte[] data) {
            boolean found = false;
            while (true) {
                try {
                    Stat stat = new Stat();
                    byte[] retrievedData = zk.getData(zkPath, false, stat);
                    /* check if this node was created by this process. In order to 
                 do so, compare the zNode's stored data with the initialization data
                 for that node.                    
                     */
                    if (Arrays.equals(retrievedData, data)) {
                        found = true;
                    } else {
                        error();
                    }
                } catch (KeeperException.NoNodeException e) {
                    // no node, so try create again
                    break;
                } catch (KeeperException.ConnectionLossException e) {
                    LOG.warn("Connection loss was detected. Retrying...");
                } catch (KeeperException ex) {
                    LOG.error("Something went wrong: ", ex);
                    error();
                    break;
                } catch (InterruptedException ex) {
                    // log the event
                    LOG.warn("Thread Interruped. Stopping.");
                    // set the interrupt status
                    Thread.currentThread().interrupt();
                    error();
                    break;
                }
            }
            return found;
        }

        private void error() {
            error = true;
        }
    }

    /**
     * Deletes the file specified.
     *
     * @param filePath path of the file to delete.
     * @throws IOException if an I/O error occurs.
     */
    private void deleteFile(String filePath) throws IOException {
        LOG.info("Deleting file {}.", filePath);
        Files.deleteIfExists(Paths.get(filePath));
    }

    private class InfoNode {

        private String zipSha256;
        private String downloadPath;
        private int numOfDataNodes;
        private String accessMode;

        private InfoNode(String zipSha256, String downloadPath, int numOfDataNodes) {
            this.zipSha256 = zipSha256;
            this.downloadPath = downloadPath;
            this.numOfDataNodes = numOfDataNodes;
        }

        private InfoNode(byte[] fromByteArr) {
            String dataStr = new String(fromByteArr);
            String[] tokens = dataStr.split(",");
            if (tokens.length != 3) {
                throw new IllegalArgumentException("Expecting three comma seperated values.");
            } else {
                zipSha256 = tokens[0];
                downloadPath = tokens[1];
                numOfDataNodes = Integer.parseInt(tokens[2]);
            }
        }

        @Override
        public String toString() {
            return zipSha256 + "," + downloadPath + "," + numOfDataNodes;
        }

        private byte[] toByteArr() {
            String dataStr = this.toString();
            return dataStr.getBytes();
        }
    }

}
