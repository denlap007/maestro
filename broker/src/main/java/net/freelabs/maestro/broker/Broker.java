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
package net.freelabs.maestro.broker;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.zookeeper.ConnectionWatcher;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeCreated;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that defines a Broker client to the zookeeper configuration store. Must
 * implement the BrokerInterface.
 */
public class Broker extends ConnectionWatcher implements BrokerInterface, Runnable {

    /**
     * The path of the Container to the zookeeper namespace.
     */
    private final String zkContainerPath;
    /**
     * Initial data for the zookeeper Broker node.
     */
    private static final String BROKER_ID = Long.toString(new Random().nextLong());
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Broker.class);
    /**
     * Latch Object with a count of one.
     */
    private final CountDownLatch shutdownSignal = new CountDownLatch(1);
    /**
     * The path of the zNode that indicates the applications shutdown.
     */
    private final String shutdownNode;
    /**
     * The path of the zNode that holds the user configuration for the
     * container.
     */
    private final String userConfNode;
    /**
     * The user configuration for the container.
     */
    private String containerConf;

    /**
     * Constructor
     *
     * @param zkHosts the zookeeper hosts list.
     * @param zkSessionTimeout the client session timeout.
     * @param zkContainerPath the path of the Container to the zookeeper
     * namespace.
     * @param namingService the path of the naming service to the zookeeper
     * namespace.
     * @param shutdownNode the node the signals the shutdown.
     * @param userConfNode the node with the initial container configuration.
     */
    public Broker(String zkHosts, int zkSessionTimeout, String zkContainerPath, String namingService, String shutdownNode, String userConfNode) {
        super(zkHosts, zkSessionTimeout);
        this.zkContainerPath = zkContainerPath;
        this.shutdownNode = shutdownNode;
        this.userConfNode = userConfNode;
    }

    /**
     * Method implementation of run method from Runnable interface.
     */
    @Override
    public void run() {
        runBroker();
    }

    @Override
    public void runBroker() {
        // set watch for shutdown zNode
        setShutDownWatch();
        // create container zNode
        createZkNode();
        // set watch for container configuration
        setConfWatch();
        // wait for shutdown
        waitForShutdown();
        // close zk client session
        stop();
    }

    /**
     * Creates the Container zkNode. The node is EPHEMERAL.
     */
    @Override
    public void createZkNode() {
        zk.create(zkContainerPath, BROKER_ID.getBytes(), OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL, createBrokerCallback, null);
    }

    /**
     * The object to call back with the
     * {@link #createContainer(String) createContainer} method.
     */
    private final StringCallback createBrokerCallback = (int rc, String path, Object ctx, String name) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkContainerNode();
                break;
            case NODEEXISTS:
                LOG.error("Node already exists: " + path);
                break;
            case OK:
                LOG.info("Created zNode: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Checks weather the container is created or not.
     */
    private void checkContainerNode() {
        zk.getData(zkContainerPath, false, checkBrokerCallback, null);
    }

    /**
     * The object to call with the {@link #checkBroker() checkBroker} method.
     */
    private final DataCallback checkBrokerCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkContainerNode();
                break;
            case NONODE:
                createZkNode();
                break;
            case OK:
                String nodeId = new String(data);
                // check if this is the container node
                if (nodeId.equals(BROKER_ID) == true) {
                    LOG.info("Created zNode: " + path);
                } else {
                    LOG.error("Cannot create zNode. Node already exists: " + path);
                }
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    @Override
    public void registerToServices(String namingService) {

    }

    @Override
    public void getUserConf() {
        zk.getData(userConfNode, false, getUserConfCallback, null);
    }

    private final DataCallback getUserConfCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getUserConf();
                break;
            case NONODE:
                LOG.error("Node does NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("ZkNode initialized with configuration: " + path);
                // deserialize configuration
                containerConf = deserializeConf(data);
                // set data to container zNode 
                setNodeData(data);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Checks if configuration for this container exists and sets a watch.
     */
    private void setConfWatch() {
        zk.exists(userConfNode, setConfWatchWatcher, setConfWatchCallback, null);
    }

    private final AsyncCallback.StatCallback setConfWatchCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                setConfWatch();
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.info("Configuration Node found: " + path);
                // get the configuration from the configuration zNode
                getUserConf();
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * A watcher to process a watch notification for configuration node.
     */
    private final Watcher setConfWatchWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            getUserConf();
        }
    };

    /**
     * Sets a watch on the zookeeper shutdown node.
     */
    private void setShutDownWatch() {
        zk.exists(shutdownNode, shutDownWatcher, shutDownCallback, null);
    }
    /**
     * Callback to be used with {@link #setShutDownWatch() setShutDownWatch()}
     * method.
     */
    private final StatCallback shutDownCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
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
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * A watcher to process a watch notification for shutdown node.
     */
    private final Watcher shutDownWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            shutdown();
        }
    };

    /**
     * Sets a latch to wait for shutdown.
     */
    private void waitForShutdown() {
        try {
            shutdownSignal.await();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Interruption attemplted", ex);
            // set interrupted flag
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Releases the latch to initiate shutdown.
     */
    private void shutdown() {
        shutdownSignal.countDown();
        LOG.info("Initiating Broker shutdown " + zkContainerPath);
    }

    /**
     * Deserializes configuration.
     *
     * @param data the data to be deserialized.
     * @return the deserialized data as String.
     */
    private String deserializeConf(byte[] data) {
        String str = JsonSerializer.deserialize(data);
        LOG.info("Configuration deserialized! Printing: " + str);
        return str;
    }

    /**
     * Sets data to container's zNode.
     */
    private void setNodeData(byte[] data) {
        zk.setData(zkContainerPath, data, -1, setNodeDataCallback, data);
    }

    /**
     * Callback to be used with {@link #setNodeData(byte[]) setNodeData(byte[])} method.
     */
    private final StatCallback setNodeDataCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                setNodeData((byte[]) ctx);
                break;
            case NONODE:
                LOG.error("Cannot set data to node. NODE DOES NOT EXITST: " + path);
                break;
            case OK:
                LOG.info("Data set to node: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }

    };

    // ------------------------------- MAIN ------------------------------------
    public static void main(String[] args) throws IOException, InterruptedException {
        // Create and initialize broker
        Broker broker = new Broker(args[0], // zkHosts
                Integer.parseInt(args[1]), // zkSessionTimeout
                args[2], // zkContainerPath
                args[3], // namingService
                args[4], // shutdownNode
                args[5] // userConfNode
        );

        // connect to zookeeper
        broker.connect();
        // create a new thread and start broker
        Thread thread = new Thread(broker, "BrokerThread-" + args[2]);
        thread.start();
    }

}
