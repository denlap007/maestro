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

import generated.Container;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import net.freelabs.maestro.serializer.Serializer;
import net.freelabs.maestro.zookeeper.ConnectionWatcher;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.AsyncCallback.DataCallback;
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
     * A {@link net.freelabs.maestro.generated.Container Container} object that
     * holds the initial configuration.
     */
    private Container con;
    /**
     * A shell command executor to execute commands.
     */
    private ShellCommandExecutor cmdExec;
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

    private final CountDownLatch initConfSetSignal = new CountDownLatch(1);

    private final CountDownLatch shutdownSignal = new CountDownLatch(1);

    private boolean isInitConfRead = false;
    
    private final String shutdownNode;
    
    private byte[] initConf;

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
     */
    public Broker(String zkHosts, int zkSessionTimeout, String zkContainerPath, String namingService, String shutdownNode) {
        super(zkHosts, zkSessionTimeout);
        this.zkContainerPath = zkContainerPath;
        this.shutdownNode = shutdownNode;
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
        setShutDownWatch();
        createContainer(zkContainerPath);
        getInitConf();
        waitForInitConf();
        deserializeConf(initConf);
        waitForShutdown();
        stop();
    }

    /**
     * Creates the Container zkNode. The node is EPHEMERAL.
     */
    @Override
    public void createContainer(String zkPath) {
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
                checkBroker();
                break;
            case NODEEXISTS:
                LOG.error("Node already exists: " + path);
                break;
            case OK:
                LOG.info("Container started: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Checks weather the container is created or not.
     */
    public void checkBroker() {
        zk.getData(zkContainerPath, false, checkBrokerCallback, null);
    }

    /**
     * The object to call with the {@link #checkBroker() checkBroker} method.
     */
    private final DataCallback checkBrokerCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkBroker();
                break;
            case NONODE:
                createContainer(path);
                break;
            case OK:
                String nodeId = new String(data);
                // check if this is the container node
                if (nodeId.equals(BROKER_ID) == true) {
                    LOG.info("Container started: " + path);
                } else {
                    LOG.error("Cannot start container. Node already exists: " + path);
                }
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    @Override
    public void inspectContainer(ShellCommandExecutor cmdExec, String containerName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void registerContainer(String namingService) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    public void getInitConf() {
        zk.getData(zkContainerPath, getInitConfWatcher, getInitConfCallback, initConf);
    }

    private final DataCallback getInitConfCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getInitConf();
                break;
            case OK:
                String nodeData = new String(data);
                // if data updated continue
                if (BROKER_ID.equals(nodeData) == false) {
                    LOG.info("Container initialized with configuration from master!" + path);
                    initConf = data;
                    isInitConfRead = true;
                    unwaitForInitConf();
                }
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    private final Watcher getInitConfWatcher = new Watcher() {

        public void process(WatchedEvent event) {
            if (event.getType() == Watcher.Event.EventType.NodeDataChanged && isInitConfRead == false) {
                LOG.info("Container initialized with configuration from master!");
                zk.getData(zkContainerPath, null, getInitConfCallback, initConf);
                unwaitForInitConf();
            }
        }
    };

    private void unwaitForInitConf() {
        initConfSetSignal.countDown();
    }

    /**
     * Waits until the initial configuration is set from the master.
     */
    public void waitForInitConf() {
        try {
            initConfSetSignal.await();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Interruption attemplted", ex);
            // set interrupted flag
            Thread.interrupted();
        }
    }

    public void waitForShutdown() {
        try {
            shutdownSignal.await();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Interruption attemplted", ex);
            // set interrupted flag
            Thread.interrupted();
        }
    }

    /**
     * Sets a watch on the zookeeper shutdown node.
     */
    public void setShutDownWatch() {
        zk.exists(shutdownNode, shutDownWatcher, shutDownCallback, zk);
    }

    private final AsyncCallback.StatCallback shutDownCallback = (int rc, String path, Object ctx, Stat stat) -> {
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
     * A watcher to process a watch notification.
     */
    private final Watcher shutDownWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            shutdown();
        }
    };
    
    /**
     * Releases the latch to initiate shutdown.
     */
    public void shutdown() {
        shutdownSignal.countDown();
        LOG.info("Initiating Container shutdown " +  zkContainerPath);
    }
    
    public void deserializeConf(byte [] data){
        try { 
            con = (Container) Serializer.deserialize(data);
            LOG.info("Configuration deserialized");
        } catch (IOException | ClassNotFoundException ex) {
            LOG.error("Something went wrong: ", ex);
        }
    }

   

}
