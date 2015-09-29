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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import net.freelabs.maestro.serialize.Serializer;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.AsyncCallback.VoidCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/**
 *
 * Class that bootstraps zookeeper, creates hierarchical namespace.
 */
public class ZkMasterAsync implements Watcher {

    /**
     * The zookeeper handle.
     */
    private ZooKeeper zk;
    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads.
     */
    private final CountDownLatch connectedSignal = new CountDownLatch(1);
    /**
     * Zookeeper configuration.
     */
    private final ZookeeperConfig zkConf;
    /**
     * The path of master to the zookeeper hierarchical namespace.
     */
    private static final String MASTER_PATH = "/master";

    private static boolean isRunning = false;

    private static final Logger LOG = LoggerFactory.getLogger(ZkMasterAsync.class);

    private List<String> createdZkNodes = new ArrayList<>();
    /**
     * Data for the master node.
     */
    private static final String masterId = Long.toString(new Random().nextLong());

    /**
     * Constructor
     *
     * @param zkConf the zookeeper configuration
     */
    public ZkMasterAsync(ZookeeperConfig zkConf) {
        this.zkConf = zkConf;
    }

    /**
     * <p>
     * Creates a new zookeeper handle and waits until connection to the
     * zookeeper server is established.
     * <p>
     * The call to the constructor returns immediately, so it is important to
     * wait for the connection to be established before using the ZooKeeper
     * object. We make use of Java’s CountDownLatch class (in the
     * java.util.concurrent package) to block until the ZooKeeper instance is
     * ready.
     *
     * @throws IOException if connection cannot be established.
     * @throws InterruptedException if thread is interrupted while waiting.
     */
    public void connect() throws InterruptedException, IOException {
        zk = new ZooKeeper(zkConf.getHosts(), zkConf.getSESSION_TIMEOUT(), this);
        connectedSignal.await();
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
            LOG.info("Connection established");
            connectedSignal.countDown();
        }
    }

    /**
     * Closes the client session of a zookeeper handle.
     *
     * @throws Exception
     */
    public void stopZK() throws Exception {
        zk.close();
    }

    synchronized public void updateNodeData(String path, byte[] data) {

    }

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    /**
     * Creates the master zkNode. The node is EPHEMERAL with masterID data.
     */
    public void runMaster() {
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
                return;
            case NONODE:
                runMaster();
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
     * Initiates an exists() operation for the master and registers a watch.
     */
    public void masterExists() {
        zk.exists(MASTER_PATH, masterExistsWatcher, masterExistsCallback, null);
    }
    /**
     * The watcher to be used with (@link #masterExists() masterExists) method.
     */
    private final Watcher masterExistsWatcher = new Watcher() {
        @Override
        public void process(WatchedEvent event) {
            if (event.getType() == Watcher.Event.EventType.NodeCreated) {
                LOG.info("BOOTSTRAPING creation of parent namespace!");
                bootstrap(zkConf.getZkParents());

                LOG.info("REGISTERING watches for children namespace!");
                registerChildrenWatches();
            }
        }
    };
    /**
     * The callback object to be used with (@link #masterExists() masterExists)
     * method.
     */
    private final StatCallback masterExistsCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                masterExists();
                return;
            case NONODE:
                LOG.info("Master does not exist yet!");
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
     * @param parentNodes
     */
    public void bootstrap(List<ZookeeperNode> parentNodes) {
        // Create the parent zkNodes
        parentNodes.stream().forEach((node) -> {
            createParent(node.getPath(), node.getData());
        });
    }

    /**
     * Creates a zNode.
     *
     * @param path the path of the zNode to create.
     * @param data the data of the zNode.
     */
    public void createParent(String path, byte[] data) {
        zk.create(path,
                data,
                OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT,
                createParentCallback,
                data);

        createdZkNodes.add(path);
    }

    /**
     * Callback object for create operation.
     */
    private final StringCallback createParentCallback = (int rc, String path, Object ctx, String name) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                createParent(path, (byte[]) ctx);
                break;
            case OK:
                LOG.info("Created node: " + path);
                break;
            case NODEEXISTS:
                LOG.warn("Node already registered: " + path);
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
        // get the created parent nodes
        for (String parent : createdZkNodes) {
            try {
                // get the children of the parent node
                List<String> children = zk.getChildren(parent, false);
                // delete children if any of the parent
                for (String child : children) {
                    deleteNodeSync(parent + "/" + child, -1);
                }
                
                // delte parent node
                deleteNodeSync(parent, -1);

            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss was detected");
                cleanUp();
            } catch (KeeperException ex) {
                LOG.error("Something went wrong", ex);
            }
        }
    }

    public void deleteNodeSync(String path, int version) throws InterruptedException {
        try {
            zk.delete(path, version);
            LOG.warn("Deleted node: " + path);
        } catch (ConnectionLossException ex) {
            LOG.warn("Connection loss was detected");
            deleteNodeSync(path, version);
        } catch (KeeperException ex) {
            LOG.error("Something went wrong", ex);
        }
    }

    /**
     * Delete a zkNode.
     *
     * @param path the path to delete.
     * @param version the data version of the zkNode to delete.
     */
    public void deleteNodeAsync(String path, int version) {
        zk.delete(path, version, deleteCallback, version);
    }
    /**
     * Callback object for delete operation.
     */
    private final VoidCallback deleteCallback = (int rc, String path, Object ctx) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                deleteNodeAsync(path, (int) ctx);
                return;
            case NONODE:
                LOG.error("Path was not found: " + path);
                break;
            case OK:
                LOG.info("Deleting path: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(Code.get(rc), path));
        }
    };

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    public void registerChildrenWatches() {
        // Get the list with the children nodes
        List<ZookeeperNode> children = zkConf.getZkChildren();

        for (ZookeeperNode child : children) {
            // get the path of the node
            String path = child.getPath();
            // register watch
            childExists(path);
        }
    }

    /**
     * Register watch for a child.
     *
     * @param path the path to be checked.
     */
    public void childExists(String path) {
        zk.exists(path, childExistsWatcher, childExistsCallback, null);
    }

    /**
     * <p>
     * The watcher to be used with (@link #childExists() childExists) method.
     * <p>
     * The watcher processes a NodeCreated event. When the watching node is
     * created a call to (@link #setChildData(String, byte[]) setChildData)
     * method is initiated, in order to set the data on the newly created node.
     */
    private final Watcher childExistsWatcher = new Watcher() {
        @Override
        public void process(WatchedEvent event) {
            LOG.info(event.getType() + ", " + event.getPath());
            
            if (event.getType() == Watcher.Event.EventType.NodeCreated) {
                // get the list of children
                List<ZookeeperNode> children = zkConf.getZkChildren();
                //find the child in the zookeeper conf
                for (ZookeeperNode child : children) {
                    if (child.getPath().equals(event.getPath())) {
                        LOG.info("SETTING DATA to node: " + event.getPath());
                        // set data to child node
                        setChildData(child.getPath(), child.getData());
                    }
                }
            }
        }
    };

    /**
     * Callback object to be used with (@link #childExists() childExists)
     * method.
     */
    private final StatCallback childExistsCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                childExists(path);
                return;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.error("Child exists: " + path);
                ctx = stat;
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
     * Sets data to child node.
     *
     * @param path the path of the node to set data.
     * @param data the data to be set to the node.
     */
    public void setChildData(String path, byte[] data) {
        zk.setData(path, data, -1, setChildDataCallback, data);
    }

    /**
     * Callback object to be used with (@link #setChildData() setChildData)
     * method.
     */
    private final StatCallback setChildDataCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (Code.get(rc)) {
            case CONNECTIONLOSS:
                setChildData(path, (byte[]) ctx);
                return;
            case NONODE:
                LOG.error("Child does not exist: " + path);
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

    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

    /*private final ChildrenCallback cleanUpkCallback = (int rc, String path, Object ctx, List<String> children) -> {
     switch (Code.get(rc)) {
     case CONNECTIONLOSS:
     cleanUp();
     return;
     case NONODE:
     LOG.error("Path was not found: " + path);
     break;
     case OK:
     LOG.info("Printing children of: " + path);
     children.stream().forEach((child) -> {
     LOG.info(child);
     });
     }
     };*/
    /* ------------------------------ TEST ---------------------------------- */
    public static void main(String[] args) throws InterruptedException, IOException, ClassNotFoundException, KeeperException {
        String hosts = "127.0.0.1:2181";
        int timeout = 5000;
        // Create zookeeper configuration
        ZookeeperConfig zkConf = new ZookeeperConfig(hosts, timeout);

        //create serializer
        //byte[] data = Serializer.serialize(node);
        zkConf.initParentNode("web", "".getBytes());

        zkConf.initChildNode("childTestNode", "web", "childData".getBytes());

        // Create master object
        ZkMasterAsync master = new ZkMasterAsync(zkConf);
        // create a session
        master.connect();
        // create master zkNode
        master.masterExists();
        master.runMaster();
        Thread.sleep(30000);


        
        
        /*master.bootstrap(zkConf.getZkParents());
         Thread.sleep(5000);
         byte[] retrievedData = master.checkData("/web");
         ZookeeperNode node2 = (ZookeeperNode) Serializer.deserialize(retrievedData);

         System.out.println("Printing de-serialized node data: " + node2.getPath());
         */
        //Thread.sleep(10000);
        // Clean up created namespace
        master.checkData("/web/childTestNode");
        master.cleanUp();
    }

}
