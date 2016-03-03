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
package net.freelabs.maestro.core.broker;

import net.freelabs.maestro.core.zookeeper.ZkExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkNode;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeCreated;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * <p>
 * Abstract class that provides methods to initialize and boot a container.
 * <p>
 * For every container type, there is a subclass of this class that handles
 * initialization and bootstrapping of the container.
 */
public abstract class CoreBroker implements Runnable {

    /**
     * The container associated with the broker.
     */
    protected final Container con;
    /**
     * The zookeeper configuration.
     */
    protected final ZkConf zkConf;
    /**
     * The docker client that will communicate with the docker daemon.
     */
    protected final DockerClient dockerClient;
    /**
     * A Logger object.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(CoreBroker.class);
    /**
     * The container id.
     */
    private String CID;
    /**
     * A ZkNode object that holds all the zk configuration about this container.
     */
    protected final ZkNode zNode;
    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads.
     */
    private final CountDownLatch shutdownSignal = new CountDownLatch(1);
    /**
     * Number of times to try and execute code passed to null null null null
     * null null null     {@link 
     * #runAndRetry(net.freelabs.maestro.core.broker.CoreBroker.RunCmd, int) runAndRetry}.
     */
    private static final int RETRY_ATTEMPTS = 3;
    /**
     * An object implementing the {@link ZkExecutor ZkExecutor} interface. This
     * object delegates zookeeper requests to a zk handle.
     */
    private final ZkExecutor zkClient;

    /**
     * Handles errors.
     */
    //private final ErrorHandler errHandler; 

    /**
     * Constructor
     *
     * @param zkConf the zookeeper configuration.
     * @param con the container which will be bound to the broker.
     * @param dockerClient a docker client to communicate with the docker
     * daemon.
     * @param zkClient a zkClient that will make requests to zookeeper. //@param
     * errHandler a handler to call in case of error.
     */
    public CoreBroker(ZkConf zkConf, Container con, DockerClient dockerClient, ZkExecutor zkClient) {
        this.zkConf = zkConf;
        this.con = con;
        this.dockerClient = dockerClient;
        this.zkClient = zkClient;
        //this.errHandler = errHandler;
        zNode = zkConf.getZkAppConf().getContainers().get(con.getName());
    }

    @Override
    public void run() {
        // run Broker 
        runBroker();
    }

    private void runBroker() {
        // boot the container
        CID = bootContainer();
        // check for errors
        if (CID != null) {
            // get container IP
            String IP = getContainerIP(CID);
            // update container ip
            updateIP(IP);

            try {
                // update zNode configuration
                zNode.setData(JsonSerializer.serialize(con));
                // log the event
                LOG.info("Updated configuration of: {}, {}:{}", zNode.getName(), "IP", IP);
            } catch (JsonProcessingException ex) {
                LOG.error("FAILED to update container IP. ", ex);
            }

            // create zk configuration node
            zkClient.zkExec((zk) -> {
                createNode(zk, zNode.getConfNodePath(), zNode.getData());
            });
            // Sets the thread to wait until it's time to shutdown
            waitForShutdown();
        } else {
            LOG.error("Could NOT start container. Shutting down Broker.");
            shutdown();
        }
    }

    /**
     * Boots a container. The method initially defines the necessary
     * configuration for the environment and then for the container.
     *
     * @return the container ID of the started container.
     */
    protected abstract String bootContainer();

    /**
     * <p>
     * Generates the container boot environment.
     * <p>
     * The container boot environment is all the necessary configuration for the
     * environment of the container in order to boot.
     *
     * @return a String with key/value pairs in the form key1=value1,
     * key2=value2 e.t.c. representing the container description.
     */
    protected abstract String createBootEnv();

    /**
     * Creates a container based on the docker settings specified.
     *
     * @return an instance of response to the create command.
     */
    protected abstract CreateContainerResponse createContainer();

    /**
     * Updates the IP of the container.
     *
     * @param IP the container IP.
     */
    protected abstract void updateIP(String IP);

    /**
     * Gets the IP of the container with this container ID.
     *
     * @param containerId
     * @return
     */
    private String getContainerIP(String containerId) {
        // inspect container with id
        InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
        // get network settings 
        InspectContainerResponse.NetworkSettings settings = response.getNetworkSettings();
        // return the cotnainer's IP
        return settings.getIpAddress();
    }

    /**
     * Creates a zNode.
     *
     * @param zk a zookeeper handle.
     * @param path the path of the zNode.
     * @param data the data of the zNode.
     */
    public void createNode(ZooKeeper zk, String path, byte[] data) {
        zk.create(path, data, OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, createNodeCallback, data);
    }

    /**
     * Callback object to be used with
     * {@link #createNode(String, byte[]) createNode} method.
     */
    private final AsyncCallback.StringCallback createNodeCallback = new AsyncCallback.StringCallback() {
        @Override
        public void processResult(int rc, String path, Object ctx, String name) {
            switch (KeeperException.Code.get(rc)) {
                case CONNECTIONLOSS:
                    LOG.warn("Connection loss was detected");
                    zkClient.zkExec((zk) -> {
                        checkNode(zk, path, (byte[]) ctx);
                    });
                    break;
                case NODEEXISTS:
                    LOG.error("Node already exists: " + path);
                    break;
                case OK:
                    LOG.info("Created zNode: " + path);
                    shutdown();
                    break;
                default:
                    LOG.error("Something went wrong: ",
                            KeeperException.create(KeeperException.Code.get(rc), path));
            }
        }
    };

    /**
     * Checks if a zNode is created.
     *
     * @param zk a zookeeper handle.
     * @param path the path of the zNode.
     * @param data the data of the zNode.
     */
    public void checkNode(ZooKeeper zk, String path, byte[] data) {
        zk.getData(path, false, checkNodeCallback, data);
    }

    /**
     * Callback object to be used with
     * {@link #checkNode(String, byte[]) checkNode} method.
     */
    private final AsyncCallback.DataCallback checkNodeCallback = new AsyncCallback.DataCallback() {
        @Override
        public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
            switch (KeeperException.Code.get(rc)) {
                case CONNECTIONLOSS:
                    LOG.warn("Connection loss was detected");
                    zkClient.zkExec((zk) -> {
                        checkNode(zk, path, (byte[]) ctx);
                    });
                    break;
                case NONODE:
                    zkClient.zkExec((zk) -> {
                        checkNode(zk, path, (byte[]) ctx);
                    });
                    break;
                case OK:
                    /* check if this node was created by this process. In order to
                    do so, compare the zNode's stored data with the initialization data
                    for that node.
                     */
                    if (Arrays.equals(data, (byte[]) ctx) == true) {
                        LOG.info("ZkNode created successfully: " + path);
                        shutdown();
                    } else {
                        LOG.error("ΖkNode exists but was NOT created by this client: " + path);
                    }
                    break;
                default:
                    LOG.error("Something went wrong: ",
                            KeeperException.create(KeeperException.Code.get(rc), path));
            }
        }
    };

    public void waitForShutdown() {
        try {
            shutdownSignal.await();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Thread Interrupted. Stopping");
            // set the interrupt status
            Thread.currentThread().interrupt();
            LOG.info("Initiating Core Broker shutdown.");
        }
    }

    public void setShutDownWatch(ZooKeeper zk) {
        zk.exists(zkConf.getZkAppConf().getShutdown().getPath(), cleanUpWatcher, cleanUpCallback, null);
    }

    private final AsyncCallback.StatCallback cleanUpCallback = new AsyncCallback.StatCallback() {
        @Override
        public void processResult(int rc, String path, Object ctx, Stat stat) {
            switch (KeeperException.Code.get(rc)) {
                case CONNECTIONLOSS:
                    zkClient.zkExec((zk) -> {
                        setShutDownWatch(zk);
                    });
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
        }
    };

    private final Watcher cleanUpWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            shutdown();
        }
    };

    public void shutdown() {
        LOG.info("Initiating Core Broker shutdown.");
        // release latch to finish execution
        shutdownSignal.countDown();
    }

    /**
     * Shuts down a container.
     *
     * @param CID the container ID.
     */
    private void shutdownContainer(String CID) {
        LOG.info("Initiating Container shutdown.");
        dockerClient.removeContainerCmd(CID).exec();
    }

    /**
     * Interface to be used as a container for code which will be passed for
     * execution.
     */
    @FunctionalInterface
    protected interface RunCmd {

        /**
         * Runs any action.
         *
         * @throws Exception in any case of Exception.
         */
        public void run() throws Exception;
    }

    /**
     * Runs the specified code and retries in case of exception.
     *
     * @param cmd the code to run passed as a lambda.
     * @param maxAttempts maximum number of attempts to run the code in case an
     * exception is thrown.
     * @return true if execution succeeded.
     */
    protected boolean runAndRetry(RunCmd cmd, int maxAttempts) {
        boolean success = false;

        while (maxAttempts > 0) {
            try {
                cmd.run();
                success = true;
                break;
            } catch (Exception ex) {
                LOG.warn("{}. Retrying.", ex.getMessage());
            }
            maxAttempts--;
        }
        return success;
    }

}
