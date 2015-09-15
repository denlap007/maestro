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
import java.util.concurrent.CountDownLatch;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

/**
 * Class that provides methods to create a client session to zookeeper, connect
 * to a zookeeper server, create new zNodes and close the session.
 */
public class CreateGroup implements Watcher {

    // The zookeeper client session time out
    private static final int SESSION_TIMEOUT = 5000;
    // The default charset to encode data
    private static final String CHARSET = "UTF-8";
    // The zookeeper handle.
    private ZooKeeper zk;
    // A CountDownLatch with a count of one, representing the number of events	
    // that need to occur before it releases all	 waiting threads
    private CountDownLatch connectedSignal = new CountDownLatch(1);

    /**
     * <p>Creates a client session to a zookeeper server, establishes connection
     * and then creates zNodes namespace hierarchy while initializing the nodes
     * with data if any.
     * <p>The namespace hierarchy is defined in namespace argument which must
     * hold an even number of arguments. As a result, the namespace argument has
     * the following structure: 
     * <pre>{zNode1_name, zNode1_data, zNode2_name, zNode2_data, ...}</pre>
     * 
     * @param hosts the list of zookeeper servers to connect along with port. 
     * The arguments has the following format: <pre>HOST1_IP:HOST1_PORT, 
     * HOST2_IP:HOST2_PORT, ...</pre>
     * @param namespace the namespace hierarchy to be created.
     * @throws IOException
     * @throws InterruptedException
     * @throws WrongArgNumberException if namespace arguments are not an even number.
     * @throws KeeperException 
     */
    public final void init(String hosts, String... namespace) throws IOException, InterruptedException, WrongArgNumberException, KeeperException {
        if ((namespace.length % 2) != 0) {
            throw new WrongArgNumberException("Wrong argument number for namespace hierarchy nodes!");
        } else {
            // Establish connection to a zookeeper server
            connect(hosts);

            // Create zNode namespace hierarchy and initialize nodes with data
            for (int i=0; i<namespace.length; i=i+2) {
                create(namespace[i], namespace[i+1]);
            }
        }

    }

    /**
     * Creates a new zookeeper handle and wait until connectino to a zookeeper
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
     * @param event
     */
    @Override
    public void process(WatchedEvent event) {	//	Watcher	interface
        if (event.getState() == KeeperState.SyncConnected) {
            connectedSignal.countDown();
        }
    }

    /**
     * Creates a zNode in the indicating path with the specified data.
     *
     * @param path the path of the new zNode.
     * @param data the data of the new zNode.
     * @throws KeeperException
     * @throws InterruptedException
     * @throws java.io.UnsupportedEncodingException
     */
    public void create(String path, String data) throws KeeperException, InterruptedException, UnsupportedEncodingException {

        //String path = "/" + groupName;
        String createdPath = zk.create(path, data.getBytes(CHARSET)/*data*/, Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT);
        System.out.println("[INFO] create(): Created	" + createdPath);
    }

    /**
     * Closes a zookeeper client session.
     *
     * @throws InterruptedException
     */
    public void close() throws InterruptedException {
        zk.close();
    }

    //------------------------------ TEST --------------------------------------
    public static void main(String[] args) throws Exception {
        CreateGroup createGroup = new CreateGroup();
        createGroup.connect("127.0.0.1:2181");
        createGroup.create("/webApp", null);
        createGroup.create("/webApp/data", "This is a namespace for data containers");
        createGroup.create("/webApp/web", "This is a namespace for web containers");
        createGroup.create("/webApp/business", "This is a namespace for business containers");
        createGroup.close();
    }
}
