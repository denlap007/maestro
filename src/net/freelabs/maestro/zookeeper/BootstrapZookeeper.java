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
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.freelabs.maestro.utils.Utils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

/**
 *
 * Class that bootstraps the zookeeper process by creating the initial zNode
 * hierarchy. Provides methods to create a client session to zookeeper, connect
 * to a zookeeper server, create new zNodes and close the session.
 */
public class BootstrapZookeeper implements Watcher {

    /**
     * The zookeeper client session time out
     */
    private static final int SESSION_TIMEOUT = 5000;
    /**
     * The default charset to encode data
     */
    private static final Charset CHARSET = Charset.forName("UTF-8");
    /**
     * The zookeeper handle.
     */
    private ZooKeeper zk;
    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads
     */
    private CountDownLatch connectedSignal = new CountDownLatch(1);

    /**
     * Constructor.
     *
     * @param hosts the zookeeper server list. The argument has the following
     * format: <pre>HOST1_IP:HOST1_PORT, HOST2_IP:HOST2_PORT, ...</pre>
     *
     * @param rootNode the root node of the zookeeper hierarchical namespace
     * WITH the root data. Field's format is: <pre> rootNode[0] = /root |
     * rootNode[1] = rootData </pre>
     *
     * @param namespace the zookeepers namespace hierarchy to be created. Must
     * follow the format: <pre>/name1, data, /name2, data ... </pre>
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws WrongArgNumberException
     * @throws KeeperException
     */
    public BootstrapZookeeper(String hosts, String[] rootNode, String... namespace) throws IOException, InterruptedException, WrongArgNumberException, KeeperException {
        init(hosts, rootNode, namespace);
    }

    /**
     * <p>
     * Creates a client session to a zookeeper server, establishes connection
     * and then creates the root zNode and the zNodes namespace hierarchy while
     * initializing the nodes with data, if any.
     * <p>
     * The namespace hierarchy is defined in namespace argument which must hold
     * an even number of arguments. As a result, the namespace argument has the
     * following format:
     * <pre>{/zNode1_name, zNode1_data, /zNode2_name, zNode2_data, ...}</pre>
     *
     * @param hosts the list of zookeeper servers to connect along with port.
     * The argument has the following format: <pre>HOST1_IP:HOST1_PORT,
     * HOST2_IP:HOST2_PORT, ...</pre>
     *
     * @param rootNode the root zookeper namespace node WITH data. Field's
     * format is: <pre> rootNode[0] = /root | rootNode[1] = rootData </pre>
     *
     * @param namespace the namespace hierarchy to be created. Must have the
     * following format: <pre>{zNode1_name, zNode1_data, zNode2_name,
     * zNode2_data, ...}</pre>
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws WrongArgNumberException if namespace arguments are not an even
     * number.
     * @throws KeeperException
     */
    public final void init(String hosts, String[] rootNode, String... namespace) throws IOException, InterruptedException, WrongArgNumberException, KeeperException {
        if ((namespace.length % 2) != 0 || (rootNode.length % 2) != 0) {
            throw new WrongArgNumberException("Wrong argument number for namespace hierarchy nodes!");
        } else {
            // Establish connection to a zookeeper server
            connect(hosts);

            // Create root zNode
            create(rootNode[0], rootNode[1]);
            // Create zNode namespace hierarchy and initialize nodes with data
            for (int i = 0; i < namespace.length; i = i + 2) {
                create(rootNode[0] + namespace[i], namespace[i + 1]);
            }
        }

    }

    /**
     * Creates a new zookeeper handle and wait until connection to a zookeeper
     * server is established.
     *
     * @param hosts the zookepers servers in the form: HOST_IP:PORT, HOST_IP:
     * @throws IOException if connection cannot be established.
     * @throws InterruptedException if thread is interrupted while waiting.
     */
    public void connect(String hosts) throws IOException, InterruptedException {
        zk = new ZooKeeper(hosts, SESSION_TIMEOUT, this);
        connectedSignal.await();
    }

    /**
     * Processes a watch event. After receiving a watch event it decreases the
     * {@link CountDownLatch} by one, indicating that one event has happened.
     *
     * @param event a watched event.
     */
    @Override
    public void process(WatchedEvent event) {	//	Watcher	interface
        if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
            connectedSignal.countDown();
        }
    }

    /**
     * Creates a new zNode in the specified path with the specified data.
     *
     * @param path the path of the new zNode.
     * @param data the data of the new zNode.
     * @throws KeeperException
     * @throws InterruptedException
     * @throws java.io.UnsupportedEncodingException
     */
    public void create(String path, String data) throws KeeperException, InterruptedException, UnsupportedEncodingException {
        byte[] bytes = null;
        if (data != null && data.isEmpty() == false) {
            bytes = data.getBytes(CHARSET);
        }

        String createdPath = zk.create(path, bytes/*data*/, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT);
        System.out.println("[INFO] create(): Created	" + createdPath);
    }

    /**
     * Creates a new zNode named memberName under the groupName path.
     *
     * @param groupName the path under which to create the new zNode.
     * @param memberName the name of the new zNode.
     * @param data the data of the new zNode.
     * @throws KeeperException
     * @throws InterruptedException
     * @throws UnsupportedEncodingException
     */
    public void create(String groupName, String memberName, String data) throws KeeperException, InterruptedException, UnsupportedEncodingException {
        byte[] bytes = null;
        // Check if data is null or empty before getting the bytes
        if (data != null && data.isEmpty() == false) {
            bytes = data.getBytes(CHARSET);
        }

        String path = "/" + groupName + "/" + memberName;
        String createdPath = zk.create(path, bytes/*data*/, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT);
        System.out.println("[INFO] create(): Created " + createdPath);
    }

    /**
     * Closes a zookeeper client session.
     *
     * @throws InterruptedException
     */
    public void close() throws InterruptedException {
        zk.close();
    }
    
    
    
    

    // ------------------------------ TEST -------------------------------------
    /*public static void main(String[] args) throws InterruptedException {

        String hosts = "127.0.0.1:2181";
        String[] rootNode = {"/webApp", ""};
        String[] namespace = {"/data",
            "data namespace", "/web", "web namespace",
            "/business", "business namespace"};

        ZookeeperConfig zkConf = new ZookeeperConfig(rootNode, hosts, namespace);
        Utils u = new Utils();
        System.out.println(Utils.toString(zkConf));

        BootstrapZookeeper bootZoo = null;
        try {
            bootZoo = new BootstrapZookeeper(hosts, rootNode, namespace);
        } catch (IOException | WrongArgNumberException | KeeperException ex) {
            Logger.getLogger(BootstrapZookeeper.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (bootZoo != null) {
                bootZoo.close();
            }
        }

    }*/
}
