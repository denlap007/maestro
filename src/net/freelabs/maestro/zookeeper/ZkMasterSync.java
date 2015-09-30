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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dionysis Lappas (dio@freelabs.net)
 */
public class ZkMasterSync implements Watcher {

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
    
    private static final String masterId = Long.toString(new Random().nextLong());


    private List<String> createdZkNodes = new ArrayList<>();

    /**
     * Constructor
     *
     * @param zkConf the zookeeper configuration
     */
    public ZkMasterSync(ZookeeperConfig zkConf) {
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

    /**
     * Processes watched events.
     *
     * @param event the processed watched event.
     */
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
            connectedSignal.countDown();
        }
    }

    /**
     * Creates the master zkNode. The node is EPHEMERAL with NO data.
     *
     * @throws java.lang.InterruptedException if thread is interrupted.
     */
    public void runMaster() throws InterruptedException {
        while (true) {
            try {
                zk.create(MASTER_PATH, masterId.getBytes(), OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                isRunning = true;
                LOG.info("Master is " + (isRunning ? "" : "NOT ") + "running!");
                break;
            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss happened!");
                checkMaster();
                break;
            } catch (NodeExistsException ex) {
                
                LOG.error("Node already exists: " + ex.getPath());
                break;
            } catch (KeeperException ex) {
                LOG.error("Something went wrong: " + ex.getMessage());
                break;
            }
        }
    }

    /**
     * Checks weather the master is created or not.
     */
    void checkMaster() throws InterruptedException {
        while (true) {
            try {
                Stat stat = new Stat();
                byte[] data = zk.getData(MASTER_PATH, false, stat);
            } catch (ConnectionLossException ex) {
                LOG.warn("Connection loss happened!");
                checkMaster();
                break;
            } catch (NodeExistsException ex) {
                LOG.error("Node already exists: " + ex.getPath());
                break;
            } catch (KeeperException ex) {
                LOG.error("Something went wrong: " + ex.getMessage());
                break;
            }
        }

    }

    /**
     * Closes the client session of a zookeeper handle.
     *
     * @throws InterruptedException
     */
    void stopZK() throws InterruptedException {
        zk.close();
    }
}
