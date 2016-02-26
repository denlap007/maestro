/*
 * Copyright (C) 2015-2016 Dionysis Lappas (dio@freelabs.net)
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
package net.freelabs.maestro.core.zookeeper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.CreateMode;
import static org.apache.zookeeper.CreateMode.PERSISTENT;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeCreated;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeDataChanged;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.data.Stat;

/**
 *
 * Class that provides methods to create a master process to initialize
 * zookeeper, create hierarchical namespace and set configuration data to
 * zkNodes declared in the zookeeper configuration.
 */
public final class ZkMaster extends ZkConnectionWatcher implements Runnable {

    /**
     * Zookeeper configuration.
     */
    private final ZkConfig zkConf;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ZkMaster.class);
    /**
     * Data for the master node.
     */
    private static final String MASTER_ID = Long.toString(new Random().nextLong());
    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads.
     */
    private final CountDownLatch shutdownSignal = new CountDownLatch(1);

    /**
     * The node that signals the shutdown of the master.
     */
    private final String shutDownNode;

    /**
     * A naming service instance to resolve container names to service names and
     * de-serialize data from service nodes.
     */
    private final ZkNamingService ns;
    /**
     * Indicates if there was an error during initialization of the Master
     * process. If so, then the master process is not properly initialized.
     */
    private boolean masterInitError;
    /**
     * Latch set when Master process starts and is released when it initializes.
     */
    private final CountDownLatch masterInitSignal = new CountDownLatch(1);

    /**
     * Constructor
     *
     * @param zkConf the zookeeper configuration
     */
    public ZkMaster(ZkConfig zkConf) {
        // initialize super class
        super(zkConf.getHosts(), zkConf.getSESSION_TIMEOUT());
        // initialize sub-class
        this.zkConf = zkConf;
        this.ns = new ZkNamingService(zkConf.getNamingServicePath());
        shutDownNode = zkConf.getShutDownPath();
    }

    @Override
    public void run() {
        // connect to zookeeper and create a session
        connectToZk();
        // check for connection errors
        if (!masterInitError) {
            // Run the master process
            runMaster();
        }
    }

    /**
     * Runs the master zk process that creates zNodes, registers watches and
     * checks master initialization. If master is initialized, the {@link
     * #masterReadySignal masterReadySignal} latch is released and any callers
     * waiting on that latch may continue execution. If shutdown is detected,
     * initiates cleanup by deleting all the znodes under the app root
     * hierarchy.
     */
    public void runMaster() {
        // watch for a cleanup zNode to cleanUp and shutdown
        setShutDownWatch();
        // create zookeeper namespace for the application
        createZkNamespace();

        if (!masterInitError) {
            // set watch for services
            setSrvWatch();
            // notify callers that master finished processing
            masterInitSignal.countDown();
            // wait until it's time for shutdown
            waitForShutdown();
        } else {
            // initiate master shutdown
            shutdown();
            // notify callers that master finished processing
            masterInitSignal.countDown();
        }
    }

    /**
     * Establishes a connection with a zookeeper server and creates a new
     * session.
     */
    private void connectToZk() {
        try {
            connect();
        } catch (IOException ex) {
            masterInitError = true;
            LOG.error("Something went wrong: " + ex);
        } catch (InterruptedException ex) {
            masterInitError = true;
            LOG.warn("Thread Interrupted. Stopping.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Creates the zookeeper hierarchical namespace defined for the application.
     */
    public void createZkNamespace() {
        // create zk root node
        LOG.info("Creating App root zNode.");
        String zkRootPath = createNode(zkConf.getZK_ROOT(), MASTER_ID.getBytes(), PERSISTENT);
        // update the App zk root path because it is a sequential node
        //zkConf.setZK_ROOT(zkRootPath);
        if (!masterInitError) {
            // create zk configuration node
            LOG.info("Creating container conf zNode.");
            createNode(zkConf.getUserConfPath(), MASTER_ID.getBytes(), PERSISTENT);
            // craete zk naming service node
            LOG.info("Creating services zNode.");
            createNode(zkConf.getNamingServicePath(), MASTER_ID.getBytes(), PERSISTENT);
            // create zk container type nodes
            LOG.info("Creating container type zNodes.");
            zkConf.getZkContainerTypes().stream().forEach((node) -> {
                createNode(node.getPath(), node.getData(), PERSISTENT);
            });
        }
    }

    /**
     * Creates a zNode.
     *
     * @param zkPath the path of the zNode.
     * @param data the data of the zNode.
     */
    private String createNode(String zkPath, byte[] data, CreateMode mode) {
        String nodePath = null;
        while (!masterInitError) {
            try {
                nodePath = zk.create(zkPath, data, OPEN_ACL_UNSAFE, mode);
                LOG.info("Created zNode: " + zkPath);
                break;
            } catch (NodeExistsException e) {
                // node exists while shoudln't
                LOG.error("Node exists: " + zkPath);
                masterInitError = true;
            } catch (ConnectionLossException e) {
                LOG.warn("Connection loss was detected. Retrying...");
            } catch (KeeperException ex) {
                LOG.error("Something went wrong: ", ex);
                masterInitError = true;
            } catch (InterruptedException ex) {
                masterInitError = true;
                // log the event
                LOG.warn("Thread Interruped. Stopping.");
                // set the interrupt status
                Thread.currentThread().interrupt();
            }
            // check if the node was created in case of ConnectionLoss
            boolean found = checkNode(zkPath, data);
            // check if there were any errors and if the node was found
            if (found) {
                break;
            }
        }
        return nodePath;
    }

    /**
     * Checks if a zNode is created.
     *
     * @param zkPath the path of the zNode to check.
     * @param data the data of the zNode.
     */
    private boolean checkNode(String zkPath, byte[] setData) {
        while (true) {
            try {
                Stat stat = new Stat();
                byte retrievedData[] = zk.getData(zkPath, false, stat);
                /* check if this node was created by this process. In order to 
                 do so, compare the zNode's stored data with the initialization data
                 for that node.                    
                 */
                if (!Arrays.equals(retrievedData, setData)) {
                    masterInitError = true;
                }
                // return true that a node is created
                return true;
            } catch (NoNodeException e) {
                // no node, so try create again
                break;
            } catch (ConnectionLossException e) {
                LOG.warn("Connection loss was detected. Retrying...");
            } catch (KeeperException ex) {
                LOG.error("Something went wrong: ", ex);
                masterInitError = true;
                break;
            } catch (InterruptedException ex) {
                masterInitError = true;
                // log the event
                LOG.warn("Thread Interruped. Stopping.");
                // set the interrupt status
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    /**
     * <p>
     * Checks if master is initialized.
     * <p>
     * The method waits until master initialization is finished, with or without
     * errors and checks if the {@link #masterInitError masterInitError} flag is
     * set.
     * <p>
     * The method blocks.
     *
     * @return true if Master process is initialized without errors.
     */
    public boolean isMasterInitialized() {
        // wait until initialization is finished (with or without errors)
        waitMasterInit();
        // check for errors
        if (!masterInitError) {
            LOG.info("Master is INITIALIZED.");
        } else {
            LOG.error("Master initialization FAILED.");
        }
        return !masterInitError;
    }

    /**
     * Sets watch for services.
     */
    private void setSrvWatch() {
        // iterate through containers map and get container names
        for (String conName : zkConf.getZkContainers().keySet()) {
            // get the zNode path of the service offered by the container
            String srvPath = ns.resolveSrvName(conName);
            // set watch
            srvExists(srvPath);
        }
    }

    /**
     * Initiates a zk exists operation on a service zNode.
     *
     * @param zkPath
     */
    private void srvExists(String zkPath) {
        zk.exists(zkPath, srvExistsnWatcher, srvExistsCallback, null);
    }

    /**
     * Callback to be used with {@link #srvExists() srvExists()} method.
     */
    private final StatCallback srvExistsCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                srvExists(path);
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.error("Node exists: " + path);
                masterInitError = true;
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * A watcher to process a watch notification for shutdown node.
     */
    private final Watcher srvExistsnWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            getSrvData(event.getPath());
        }
    };

    /**
     * Get data from the requested service zNode.
     *
     * @param zkPath the path of the service to the zookeeper namespace.
     */
    private void getSrvData(String zkPath) {
        zk.getData(zkPath, getSrvDataWatcher, getSrvDataCallback, null);
    }

    /**
     * The callback to be used with
     * {@link #getSrvData(java.lang.String) getSrvData} method.
     */
    private final DataCallback getSrvDataCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getSrvData(path);
                break;
            case NONODE:
                LOG.error("No service: " + path);
                break;
            case OK:
                // process retrieved data from requested service zNode
                processSrvData(data, path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * <p>
     * Processes data retrieved from a service zNode.
     * <p>
     * De-serializes the service node, gets the zNode path of the container
     * offering that service.
     *
     * @param data the data from a service zNode to process.
     * @param path the path of the service zNode.
     */
    private void processSrvData(byte[] data, String srvPath) {
        // de-serialize service node
        ZkNamingServiceNode node = ns.deserializeZkSrvNode(srvPath, data);
        LOG.info("Service update: {}\tStatus: {}", srvPath, node.getStatus().toString());
    }

    /**
     * Watcher to be used with {@link  #getSrvData(java.lang.String) getSrvData}
     * method.
     */
    private final Watcher getSrvDataWatcher = (WatchedEvent event) -> {
        if (event.getType() == NodeDataChanged) {
            getSrvData(event.getPath());
        }
    };

    /**
     * <p>
     * Returns a list of all the nodes of a zookeeper namespace hierarchy.
     * <p>
     * The method is called with a rootNode argument and returns a list of all
     * the nodes under this rootNode (including rootNode). The children of every
     * node are returned first!
     *
     * @param node the root node of the zookeeper namespace for the app to
     * populate hierarchy.
     * @return a list of all the nodes of the hierarchy defined under the param
     * rootNode (including rootNode).
     */
    private List<String> getAllNodes(String node) {
        // a list to hold the returned nodes
        List<String> allNodes = new ArrayList<>();
        // get the children of the node
        List<String> children = null;
        while (true) {
            try {
                children = zk.getChildren(node, false);
                break;
            } catch (InterruptedException ex) {
                // log event
                LOG.warn("Interrupted. Stopping");
                // set interupt flag
                Thread.currentThread().interrupt();
                break;
            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss was detected! Retrying...");
            } catch (NoNodeException ex) {
                LOG.info("Node already deleted: {}", ex.getMessage());
                break;
            } catch (KeeperException ex) {
                LOG.error("Something went wrong", ex);
                break;
            }
        }
        // if node has children, for every child recurse and add returned node to list
        if (children != null) {
            if (!children.isEmpty()) {
                for (String child : children) {
                    List<String> tmpList = getAllNodes(node + "/" + child);
                    allNodes.addAll(tmpList);
                }
            }
            // add the node with wich the method was called to the list
            allNodes.add(node);
        }
        return allNodes;
    }

    /**
     * Cleans the zookeeper namespace from all the nodes created by the
     * application.
     */
    public void cleanZkNamespace() {
        LOG.info("Cleaning zookeeper namespace.");

        List<String> nodesToDelete = getAllNodes(zkConf.getZK_ROOT());
        ListIterator<String> iter = nodesToDelete.listIterator();

        while (iter.hasNext()) {
            // get a node
            String node = iter.next();
            // delete the node, don't check for version
            deleteNode(node, -1);
        }
    }

    /**
     * Deletes the specified zNode. The zNode mustn't have any children.
     *
     * @param path the zNode to delete.
     * @param version the data version of the zNode.
     */
    public void deleteNode(String path, int version) {
        while (true) {
            try {
                zk.delete(path, version);
                LOG.info("Deleted node: {}", path);
                break;
            } catch (InterruptedException ex) {
                // log event
                LOG.warn("Interrupted. Stopping.");
                // set interupt flag
                Thread.currentThread().interrupt();
                break;
            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss was detected. Retrying...");
            } catch (NoNodeException ex) {
                LOG.info("Node already deleted: {}", path);
                break;
            } catch (KeeperException ex) {
                LOG.error("Something went wrong", ex);
                break;
            }
        }
    }

    /**
     * Checks the data from a zkNode. FOR DEBUGGIND.
     *
     * @param path the path of the znode to check.
     * @return the size of the znode data in bytes.
     * @throws org.apache.zookeeper.KeeperException error handling from zk.
     * @throws java.lang.InterruptedException if interrupted.
     */
    public byte[] checkData(String path) throws KeeperException, InterruptedException {
        byte[] data = zk.getData(path, false, null);
        LOG.info("Data length is: " + data.length);
        LOG.info("Data is: " + new String(data));
        return data;
    }

    /**
     * Sets data to container node.
     *
     * @param path the path of the node to set data.
     * @param data the data to be set to the node.
     */
    public void setContainerData(String path, byte[] data) {
        zk.setData(path, data, -1, setChildDataCallback, data);
    }

    /**
     * Callback object to be used with (@link #setChildData() setChildData)
     * method.
     */
    private final StatCallback setChildDataCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                setContainerData(path, (byte[]) ctx);
                break;
            case NONODE:
                LOG.error("Container does not exist: " + path);
                break;
            case OK:
                LOG.info("Data set to node: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
                break;
        }
    };

    /**
     * Blocks until shutdown.
     *
     */
    public void waitForShutdown() {
        try {
            shutdownSignal.await();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Thread Interruped. Stopping.");
            // set the interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initiates shutdown.
     */
    public void shutdown() {
        LOG.warn("Initiating master shutdown.");
        // delete nanespace
        cleanZkNamespace();
        try {
            // close session
            stop();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Thread Interruped. Stopping.");
            // set the interrupt status
            Thread.currentThread().interrupt();
        }
        // release latch to finish execution
        shutdownSignal.countDown();
    }

    /**
     * Sets a watch for the znode that indicates the program shutdown.
     */
    private void setShutDownWatch() {
        zk.exists(shutDownNode, shutDownWatcher, setShutDownWatchCallback, zk);
    }
    /**
     * Callback to be used with {@link #setShutDownWatch() setShutDownWatch}.
     */
    private final StatCallback setShutDownWatchCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                setShutDownWatch();
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.error("Node exists: " + path);
                masterInitError = true;
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };
    /**
     * Watcher to process watched event: shutdown node created.
     */
    private final Watcher shutDownWatcher = (WatchedEvent event) -> {
        if (event.getType() == NodeCreated) {
            LOG.info(event.getType() + ", " + event.getPath());
            shutdown();
        }
    };

    /**
     * <p>
     * Waits until the Master process initializes.
     * <p>
     * Method blocks.
     */
    private void waitMasterInit() {
        try {
            masterInitSignal.await();
        } catch (InterruptedException ex) {
            // log event
            LOG.warn("Interrupted. Stopping.");
            // set interupt flag
            Thread.currentThread().interrupt();
        }
    }

    public void createShutdownNode() {

    }

    public void display() {
        try {
            List<String> children = zk.getChildren(zkConf.getZK_ROOT(), false);
            LOG.info("Printing children of ROOT.");
            for (String child : children) {
                LOG.info("Node: " + child);
            }
        } catch (KeeperException | InterruptedException ex) {
            LOG.error("Something went wrong: ", ex);
        }
    }

}
