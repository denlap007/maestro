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
package net.freelabs.maestro.zookeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
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
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.data.Stat;

/**
 *
 * Class that provides methods to create a master process to initializes
 * zookeeper, create hierarchical namespace and set configuration data to
 * zkNodes declared in the zookeeper configuration.
 */
public final class ZkMaster extends ConnectionWatcher {
    /**
     * Zookeeper configuration.
     */
    private final ZookeeperConfig zkConf;
    /**
     * The path of master to the zookeeper hierarchical namespace.
     */
    private final String MASTER_PATH;
    /**
     * A boolean value indicating if the master is running.
     */
    private static boolean isRunning = false;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ZkMaster.class);
    /**
     * Data for the master node.
     */
    private static final String masterId = Long.toString(new Random().nextLong());
    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads.
     */
    private final CountDownLatch shutdownSignal = new CountDownLatch(1);
    /**
     * A map of containers (Key) and their STATE (Value).
     */
    private Map<String, CONTAINER_STATE> containerState = new HashMap<>();
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
    public ZkMaster(ZookeeperConfig zkConf) {
        // initialize super class
        super(zkConf.getHosts(), zkConf.getSESSION_TIMEOUT());
        this.zkConf = zkConf;
        MASTER_PATH = zkConf.getZK_ROOT() + "-master";
    }

    /**
     * Runs the master zk process that creates zNodes, registers watches and
     * initializes nodes with data once created.
     *
     * @throws InterruptedException
     */
    public void runMaster() throws InterruptedException {
        // watch for a cleanup zNode to cleanUp and shutdown
        setCleanUpWatch();
        // create zk root node if present
        createZkRoot();
        // create master zNode
        createMaster();
        // create container type zNodes
        createNamespace(zkConf.getZkContainerTypes());
        // register watcher for containers in order to initialize with data
        setContainerWatches();
        // Sets the thread to wait until its time to shutdown
        waitForShutdown();
        // close session
        stop();
    }

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Creates the root zk node under which the application's node hierarchy
     * will be created.
     *
     */
    public void createZkRoot() {
        // if there is a custom zk root node create it
        if (zkConf.getZK_ROOT().equals("/") == false) {
            zk.create(zkConf.getZK_ROOT(), zkConf.getZK_ROOT().getBytes(), OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, createZkRootCallback, null);
        }
    }

    private final StringCallback createZkRootCallback = (int rc, String path, Object ctx, String name) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkZkRoot();
                break;
            case NODEEXISTS:
                LOG.error("Node already exists: " + path);
                break;
            case OK:
                LOG.info("Created ROOT node: " + path);
                break;
            default:
                isRunning = false;
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    public void checkZkRoot() {
        zk.getData(zkConf.getZK_ROOT(), false, checkZkRootCallback, zkConf.getZK_ROOT());
    }

    /**
     * The object to call with the {@link #checkMaster() checkMaster} method.
     */
    private final DataCallback checkZkRootCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkZkRoot();
                break;
            case NONODE:
                createZkRoot();
                break;
            case OK:
                String nodeId = new String(data);
                // check if this is the master node
                if (nodeId.equals(ctx) == false) {
                    LOG.error("Node already exists: " + path);
                }
                LOG.info("Node created successfully: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Creates the master zkNode. The node is EPHEMERAL with masterID as data.
     */
    public void createMaster() {
        zk.create(MASTER_PATH, masterId.getBytes(), OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL, createmasterCallback, null);
    }

    /**
     * The object to call back with the {@link #runMaster() runMaster} method.
     */
    private final StringCallback createmasterCallback = (int rc, String path, Object ctx, String name) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkMaster();
                return;
            case NODEEXISTS:
                LOG.error("Node already exists: " + path);
                break;
            case OK:
                isRunning = true;
                break;
            default:
                isRunning = false;
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
        LOG.info("Master is " + (isRunning ? "" : "NOT ") + "running!");
    };

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Checks weather the master is created or not.
     */
    public void checkMaster() {
        zk.getData(MASTER_PATH, false, masterCheckCallback, null);
    }

    /**
     * The object to call with the {@link #checkMaster() checkMaster} method.
     */
    private final DataCallback masterCheckCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkMaster();
                break;
            case NONODE:
                createMaster();
                break;
            case OK:
                String nodeId = new String(data);
                // check if this is the master node
                if (nodeId.equals(masterId) == true) {
                    isRunning = true;
                } else {
                    isRunning = false;
                    LOG.error("Node already exists: " + path);
                }

                LOG.info("Master is" + (isRunning ? "" : "NOT ") + "running!");
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Creates zookeeper namespace.
     *
     * @param containerTypes the containerTypes zk nodes to be created.
     */
    public void createNamespace(List<ZookeeperNode> containerTypes) {
        LOG.info("Creating Container Type zNodes.");
        // Create the container type zkNodes
        containerTypes.stream().forEach((node) -> {
            createContainerType(node.getPath(), node.getData());
        });
    }

    /**
     * Creates a zNode.
     *
     * @param path the path of the zNode to create.
     * @param data the data of the zNode.
     */
    public void createContainerType(String path, byte[] data) {
        zk.create(path,
                data,
                OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT,
                createParentCallback,
                data);
    }

    /**
     * Callback object for create operation.
     */
    private final StringCallback createParentCallback = (int rc, String path, Object ctx, String name) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                createContainerType(path, (byte[]) ctx);
                break;
            case OK:
                LOG.info("Created node: " + path);
                break;
            case NODEEXISTS:
                LOG.warn("Node already created: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Deletes parent zkNodes.
     *
     * @throws java.lang.InterruptedException
     */
    public void cleanUp() throws InterruptedException {
        LOG.info("Cleaning zookeeper namespace.");

        // get the created container type nodes
        for (ZookeeperNode node : zkConf.getZkContainerTypes()) {
            String parent = node.getPath();
            try {
                // get the children of the parent node
                List<String> children = zk.getChildren(parent, false);
                // delete children -if any- of the parent
                for (String child : children) {
                    deleteNode(parent + "/" + child, -1);
                }

                // delete parent node
                deleteNode(parent, -1);

            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss was detected");
                cleanUp();
            } catch (KeeperException ex) {
                LOG.error("Something went wrong", ex);
            }
        }

        if (zkConf.getZK_ROOT().equals("/") == false) {
            // delete zk root node
            deleteNode(zkConf.getZK_ROOT(), -1);
        }
    }

    public void deleteNode(String path, int version) throws InterruptedException {
        while (true) {
            try {
                zk.delete(path, version);
                LOG.info("Deleted node: " + path);
                break;
            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss was detected");
            } catch (KeeperException ex) {
                LOG.error("Something went wrong", ex);
                break;
            }
        }
    }

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Registers watches for zkChildren nodes
     */
    public void setContainerWatches() {
        // Get the list with the container nodes
        List<ZookeeperNode> containers = zkConf.getZkContainers();

        for (ZookeeperNode container : containers) {
            // get the path of the node
            String path = container.getPath();
            // register watch
            containerExists(path);
            // set state to NOT RUNNING
            containerState.put(container.getPath(), CONTAINER_STATE.NOT_RUNNING);
        }
    }

    /**
     * Registers a watch for a container.
     *
     * @param path the path to be checked.
     */
    public void containerExists(String path) {
        zk.exists(path, containerExistsWatcher, containerExistsCallback, null);
    }

    /**
     * <p>
     * The watcher to be used with (@link #childExists(String) childExists)
     * method.
     * <p>
     * The watcher processes a NodeCreated event. When the watching node is
     * created a call to (@link #setChildData(String, byte[]) setChildData)
     * method is initiated, in order to set the data on the newly created node.
     */
    private final Watcher containerExistsWatcher = new Watcher() {
        @Override
        public void process(WatchedEvent event) {
            String nodePath = event.getPath();

            LOG.info(event.getType() + ", " + nodePath);

            if (event.getType() == Watcher.Event.EventType.NodeCreated) {
                // get the list of children
                List<ZookeeperNode> containers = zkConf.getZkContainers();

                //find the container in the zookeeper conf in order to init with data
                for (ZookeeperNode container : containers) {
                    if (container.getPath().equals(nodePath)) {
                        LOG.info("Setting initial DATA to node: " + nodePath);
                        // set data to container node
                        setContainerData(container.getPath(), container.getData());

                        LOG.info("Setting STATE of node: " + nodePath + " to RUNNING");
                        //set state of container to RUNNING
                        containerState.put(container.getPath(), CONTAINER_STATE.RUNNING);

                        LOG.info("Setting WATCH for deletion to node: " + nodePath);
                        setDeletionWatch(nodePath);

                        // check if all containers are at RUNNING state
                        boolean areAllContainersRunning = !containerState.containsValue(CONTAINER_STATE.NOT_RUNNING);

                        if (areAllContainersRunning == true) {
                            LOG.info("ALL CONTAINERS ARE UP AND RUNNING.");
                        } else {
                            LOG.warn("Not all containers have started yet.");
                        }

                    }
                }
            }
        }
    };

    /**
     * Callback object to be used with (@link #childExists() childExists)
     * method.
     */
    private final StatCallback containerExistsCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                containerExists(path);
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.error("Container exists: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    private void setDeletionWatch(String path) {
        zk.exists(path, deletionWatcher, deletionCallback, null);
    }

    /**
     * <p>
     * The watcher to be used with (@link #setNodeWatch(String) setNodeWatch)
     * method.
     * <p>
     * The watcher processes a NodeDeleted event. In case a NodeDataChanged
     * event happens the watcher re-registers the watch, in order to monitor
     * again for NodeDeleted events.
     */
    private final Watcher deletionWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        // Node Deleted
        if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
            // Set node status to NOT RUNNING
            containerState.put(event.getPath(), CONTAINER_STATE.NOT_RUNNING);
            LOG.info("Setting STATE of node: " + event.getPath() + " to NOT_RUNNING");

            // Check if there is AT LEAST ONE key with NOT RUNNING value
            boolean isAnyContainerRunning = containerState.containsValue(CONTAINER_STATE.RUNNING);

            // If there in NO container at RUNNING state INITIATE MASTER SHUTDOWN
            if (isAnyContainerRunning == false) {
                shutdownMaster();
            }
        } else if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
            // If node had its data changed re-register the watch
            setDeletionWatch(event.getPath());
        }
    };

    /**
     * Callback object to be used with (@link #setNodeWatch(String) childExists)
     * method.
     */
    private final StatCallback deletionCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                setDeletionWatch(path);
                break;
            case NONODE:
                LOG.error("Cannot register watch! Node does NOT exist: " + path);
                break;
            case OK:
                LOG.info("Watch set to node: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

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

    public void shutdownMaster() {
        shutdownSignal.countDown();
        LOG.info("Initiating master shutdown.");
    }

    public void setCleanUpWatch() {
        zk.exists("/" + "cleanUp", cleanUpWatcher, cleanUpCallback, zk);
    }

    private final StatCallback cleanUpCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                setCleanUpWatch();
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

        try {
            cleanUp();
            shutdownMaster();
        } catch (InterruptedException ex) {
            LOG.error("Something went wrong: ", ex);
        }
    };

}
