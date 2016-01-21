/*
 * Copyright (C) 2015 Dionysis Lappas (dio@freelabs.net)
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

import java.util.ArrayList;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeCreated;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.data.Stat;

/**
 *
 * Class that provides methods to create a master process to initializes
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
     * A map of containers (Key) and their STATE (Value).
     */
    private final Map<String, CONTAINER_STATE> containerState = new HashMap<>();

    private volatile Map<String, STATE> zkNamespaceState = new HashMap<>();
    /**
     * The node that signals the shutdown of the master
     */
    private final String shutDownNode;

    private volatile STATE masterState;

    private enum STATE {

        INITIALIZED, NOT_INITIALIZED
    };

    private CountDownLatch masterReadySignal;

    @Override
    public void run() {
        try {
            // Run the master process
            runMaster();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Interruption attempted: ", ex);
            // set the interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The state of a container.
     */
    private static enum CONTAINER_STATE {

        NOT_RUNNING, RUNNING
    };

    /**
     * Constructor
     *
     * @param zkConf the zookeeper configuration
     */
    public ZkMaster(ZkConfig zkConf) {
        // initialize super class
        super(zkConf.getHosts(), zkConf.getSESSION_TIMEOUT());
        this.zkConf = zkConf;
        shutDownNode = zkConf.getShutDownPath();
        masterState = STATE.NOT_INITIALIZED;
    }

    /**
     * Runs the master zk process that creates zNodes, registers watches and
     * initializes nodes with data once created.
     *
     * @throws InterruptedException
     */
    public void runMaster() throws InterruptedException {
        // watch for a cleanup zNode to cleanUp and shutdown
        setShutDownWatch();
        // create zookeeper namespace for the application
        createZkNamespace();
        // check if master is initialized
        isMasterReady();
        // Sets the thread to wait until it's time to shutdown
        waitForShutdown();
        // delete nanespace
        cleanZkNamespace();
        // close session
        stop();
    }

    /**
     * Creates the zookeeper hierarchical namespace defined for the application.
     */
    public void createZkNamespace() {
        // create zk root node
        zkNamespaceState.put(zkConf.getZK_ROOT(), STATE.NOT_INITIALIZED);
        createNode(zkConf.getZK_ROOT(), MASTER_ID.getBytes());

        // create zk configuration node
        zkNamespaceState.put(zkConf.getUserConfPath(), STATE.NOT_INITIALIZED);
        createNode(zkConf.getUserConfPath(), MASTER_ID.getBytes());

        /* create zk container configuration nodes
        HashMap<String, ZkNode> map = zkConf.getZkContainers();
        for (Map.Entry pair : map.entrySet()) {
            ZkNode node = (ZkNode) pair.getValue();
            String confNode = zkConf.getUserConfPath() + node.getName();
            zkNamespaceState.put(confNode, STATE.NOT_INITIALIZED);
            createNode(confNode, node.getData());
        } */

        // craete zk naming service node
        zkNamespaceState.put(zkConf.getNamingServicePath(), STATE.NOT_INITIALIZED);
        createNode(zkConf.getNamingServicePath(), MASTER_ID.getBytes());

        // create zk container type nodes
        LOG.info("Creating zk Container Type nodes.");
        zkConf.getZkContainerTypes().stream().forEach((node) -> {
            zkNamespaceState.put(node.getPath(), STATE.NOT_INITIALIZED);
            createNode(node.getPath(), node.getData());
        });
    }

    /**
     * Creates a zNode.
     *
     * @param path the path of the zNode.
     * @param data the data of the zNode.
     */
    public void createNode(String path, byte[] data) {
        zk.create(path, data, OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, createNodeCallback, data);
    }

    /**
     * Callback object to be used with
     * {@link #createNode(String, byte[]) createNode} method.
     */
    private final StringCallback createNodeCallback = (int rc, String path, Object ctx, String name) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkNode(path, (byte[]) ctx);
                break;
            case NODEEXISTS:
                LOG.error("Node already exists: " + path);
                break;
            case OK:
                LOG.info("Created zNode: " + path);
                zkNamespaceState.put(path, STATE.INITIALIZED);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    /**
     * Checks if a zNode is created.
     *
     * @param path
     * @param data
     */
    public void checkNode(String path, byte[] data) {
        zk.getData(path, false, checkNodeCallback, data);
    }

    /**
     * Callback object to be used with
     * {@link #checkNode(String, byte[]) checkNode} method.
     */
    private final DataCallback checkNodeCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkNode(path, (byte[]) ctx);
                break;
            case NONODE:
                createNode(path, (byte[]) ctx);
                break;
            case OK:
                /* check if this node was created by this process. In order to 
                 do so, compare the zNode's stored data with the initialization data
                 for that node.                    
                 */
                if (Arrays.equals(data, (byte[]) ctx) == true) {
                    LOG.info("ZkNode created successfully: " + path);
                    zkNamespaceState.put(path, STATE.INITIALIZED);
                } else {
                    LOG.error("ΖkNode already exists: " + path);
                }
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

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

    /**
     * <p>
     * Returns a list of all the nodes of a zookeeper namespace hierarchy.
     * <p>
     * The method is called with a rootNode argument and returns a list of all
     * the nodes under this rootNode (including rootNode). The children of every
     * node are returned first!
     *
     * @param node the root node of the zookeeper namespace to populate
     * hierarchy.
     * @return a list of all the nodes of the hierarchy defined under the param
     * rootNode (including rootNode).
     * @throws KeeperException
     * @throws InterruptedException if thread is interrupted.
     */
    private List<String> getAllNodes(String node) throws KeeperException, InterruptedException {
        // a list to hold the returned nodes
        List<String> allNodes = new ArrayList<>();
        // get the children of the node
        List<String> children = zk.getChildren(node, false);
        // if node has children, for every child recurse and add returned node to list
        if (children.isEmpty() == false) {
            for (String child : children) {
                List<String> tmpList = getAllNodes(node + "/" + child);
                allNodes.addAll(tmpList);
            }
        }
        // add the node with wich the method was called to the list
        allNodes.add(node);

        return allNodes;
    }

    /**
     * Cleans the zookeeper namespace from all the nodes created by the
     * application.
     */
    public void cleanZkNamespace() {
        LOG.info("Cleaning zookeeper namespace.");

        while (true) {
            try {
                List<String> nodesToDelete = getAllNodes(zkConf.getZK_ROOT());
                ListIterator<String> iter = nodesToDelete.listIterator();

                while (iter.hasNext()) {
                    // get a node
                    String node = iter.next();
                    // delete the node, don't check for version
                    deleteNode(node, -1);
                }

                break;
            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss was detected");
            } catch (KeeperException ex) {
                LOG.error("Something went wrong", ex);
                break;
            } catch (InterruptedException ex) {
                // log event
                LOG.error("Interruption attempted", ex);
                // set interupt flag
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Deletes the specified zNode. The zNode mustn't have any children.
     *
     * @param path the zNode to delete.
     * @param version the data version of the zNode.
     * @throws InterruptedException
     */
    public void deleteNode(String path, int version) throws InterruptedException {
        while (true) {
            try {
                zk.delete(path, version);
                LOG.info("Deleted node: {}", path);
                break;
            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss was detected");
            } catch (NoNodeException ex) {
                 LOG.info("Node already detected: {}", path);
            }
            catch (KeeperException ex) {
                LOG.error("Something went wrong", ex);
                break;
            }
        }
    }

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Checks the data from a zkNode.
     *
     * @param path
     * @return
     */
    public byte[] checkData(String path) throws KeeperException, InterruptedException {
        byte[] data = zk.getData(path, false, null);
        LOG.info("Data length is: " + data.length);
        LOG.info("Data is: " + new String(data));
        return data;
    }

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
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

    public void waitForShutdown() throws InterruptedException {
        shutdownSignal.await();
    }

    public void shutdown() {
        shutdownSignal.countDown();
        LOG.info("Initiating master shutdown.");
    }

    public void setShutDownWatch() {
        zk.exists(shutDownNode, cleanUpWatcher, cleanUpCallback, zk);
    }

    private final StatCallback cleanUpCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                setShutDownWatch();
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.error("Node exists: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    private final Watcher cleanUpWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            shutdown();
        }
    };

    public boolean isZkNamespaceInitialized() {
        return !zkNamespaceState.containsValue(STATE.NOT_INITIALIZED);
    }

    /**
     * Checks if master is initialized and unblocks
     */
    public void isMasterReady() {
        Runnable checkMaster = () -> {
            while (true) {
                if (zkNamespaceState.containsValue(STATE.NOT_INITIALIZED) == false) {
                    LOG.info("Master is INITIALIZED.");
                    masterReadySignal.countDown();
                    break;
                }
            }
        };

        Thread checkMasterThread = new Thread(checkMaster);
        checkMasterThread.start();
    }

    /**
     * @param masterReadySignal the masterReadySignal to set
     */
    public void setMasterReadySignal(CountDownLatch masterReadySignal) {
        this.masterReadySignal = masterReadySignal;
    }

}
