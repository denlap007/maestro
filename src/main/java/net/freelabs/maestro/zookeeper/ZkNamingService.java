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

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.apache.zookeeper.AsyncCallback;
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
 * Class that will serve as the zookeeper naming service for the application.
 */
public class ZkNamingService extends ConnectionWatcher implements Runnable {

    /**
     * The naming service node in the zookeeper namespace.
     */
    private final String namingServicePath;
    /**
     * A boolean value indicating if the naming service is running.
     */
    private volatile static boolean isRunning = false;
    /**
     * An object with the zookeeper configuration.
     */
    private final ZkConfig zkConf;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ZkNamingService.class);
    /**
     * Data for the naming service node.
     */
    private static final String NAMING_SERVICE_ID = Long.toString(new Random().nextLong());
    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads.
     */
    private final CountDownLatch shutdownSignal = new CountDownLatch(1);
    /**
     * The node that signals the shutdown of the master
     */
    private final String shutDownNode;

    /**
     * Constructor
     *
     * @param zkConf an object with the zookeeper configuration.
     */
    public ZkNamingService(ZkConfig zkConf) {
        // call the constructor of the superclass
        super(zkConf.getHosts(), zkConf.getSESSION_TIMEOUT());
        // initialize the name of the naming service node
        this.zkConf = zkConf;
        namingServicePath = zkConf.getNamingServicePath();
        shutDownNode = zkConf.getShutDownPath();
    }

    @Override
    public void run() {
        // create the naming service zNode
        createNamingService();
        // set watch for shutdown
        setShutDownWatch();

        try {
            waitForShutdown();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Interruption attempted: ", ex);
            // set the interrupt status
            Thread.currentThread().interrupt();
        }

        // close session
        stop();
    }

    /**
     * Creates the master zkNode. The node is EPHEMERAL with masterID as data.
     */
    public void createNamingService() {
        zk.create(namingServicePath, NAMING_SERVICE_ID.getBytes(), OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT, createNamingServiceCallback, null);
    }

    /**
     * The object to call back with the {@link #runMaster() runMaster} method.
     */
    private final AsyncCallback.StringCallback createNamingServiceCallback = (int rc, String path, Object ctx, String name) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkNamingService();
                return;
            case NODEEXISTS:
                LOG.error("Node already exists: " + path);
                break;
            case OK:
                LOG.info("NAMING SERVICE started: " + path);
                isRunning = true;
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Checks weather the master is created or not.
     */
    public void checkNamingService() {
        zk.getData(namingServicePath, false, checkNamingServiceCallback, null);
    }

    /**
     * The object to call with the {@link #checkMaster() checkMaster} method.
     */
    private final AsyncCallback.DataCallback checkNamingServiceCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkNamingService();
                break;
            case NONODE:
                createNamingService();
                break;
            case OK:
                String nodeId = new String(data);
                // check if this is the master node
                if (nodeId.equals(NAMING_SERVICE_ID) == true) {
                    LOG.info("NAMING SERVICE started: " + path);
                    isRunning = true;
                } else {
                    LOG.error("Node already exists: " + path);
                }
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Sets a latch so that the thread waits for shutdown.
     *
     * @throws InterruptedException if thread is interrupted.
     */
    public void waitForShutdown() throws InterruptedException {
        shutdownSignal.await();
    }

    /**
     * Releases the latch to initiate shutdown.
     */
    public void shutdown() {
        shutdownSignal.countDown();
        LOG.info("Initiating naming service shutdown.");
    }

    /**
     * Sets a watch on the zookeeper shutdown node.
     */
    public void setShutDownWatch() {
        zk.exists(shutDownNode, shutDownWatcher, shutDownCallback, zk);
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

}
