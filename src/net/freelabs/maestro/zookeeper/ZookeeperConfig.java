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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that holds all the zookeeper pre-configuration.
 */
public final class ZookeeperConfig {

    /**
     * The list of zookeepers hosts.
     */
    private final String hosts;
    /**
     * The zookeeper client session time out.
     */
    private final int SESSION_TIMEOUT;
    /**
     * The default charset to encode data.
     */
    private static final Charset CHARSET = Charset.forName("UTF-8");
    /**
     * The zookeeper root for the namespace.
     */
    private final String ZK_ROOT;

    /**
     * A list with the zookeeper container type nodes.
     */
    private List<ZookeeperNode> zkContainerTypes = new ArrayList<>();
    /**
     * A list with the zookeeper container nodes.
     */
    private List<ZookeeperNode> zkContainers = new ArrayList<>();
    

    /**
     * Constructor.
     *
     * @param hosts the list of zookeeper hosts. Must follow the format: 
     * <pre> HOST1_IP:PORT, HOST2_IP:PORT, ...</pre>
     *
     * @param SESSION_TIMEOUT the time until session is closed if the client
     * hasn't contacted the server.
     * @param ZK_ROOT the root of the zookeeper namespace where application's node
     * hierarchy will be created.
     */
    public ZookeeperConfig(String hosts, int SESSION_TIMEOUT, String ZK_ROOT) {
        this.hosts = hosts;
        this.SESSION_TIMEOUT = SESSION_TIMEOUT;
        this.ZK_ROOT = "/" + ZK_ROOT;
    }

    /**
     * Initializes a parent (@link ZookeeperNode). The zookeeper path of the parent is
     * derived from two components: the zookeeper root + the type argument.
     * @param type container type.
     * @param data zkNode's data.
     */
    public void initZkContainerTypes(String type, byte[] data) {
        // create node's path
        String path = getZK_ROOT() + "/" + type;
        // create a new zk node object
        ZookeeperNode zkNode = new ZookeeperNode(path, data);
        // add to list
        getZkContainerTypes().add(zkNode);
    }

    /**
     * Initializes a child (@link ZookeeperNode). The zookeeper path of the child is
     * derived from three components: the zookeeper root + the type argument + 
     * name argument.
     * @param name the name of a container.
     * @param type the type of a container.
     * @param data the data of the zkNode.
     */
    public void initZkContainers(String name, String type, byte[] data) {
        // create node's path
        String path = getZK_ROOT() + "/" + type + "/" + name;
        // create a new zk node object
        ZookeeperNode zkNode = new ZookeeperNode(path, data);
        // add to list
        getZkContainers().add(zkNode);
    }

    /**
     * @return the hosts
     */
    public String getHosts() {
        return hosts;
    }

    /**
     * @return the CHARSET
     */
    public static Charset getCHARSET() {
        return CHARSET;
    }

    /**
     * @return the SESSION_TIMEOUT
     */
    public int getSESSION_TIMEOUT() {
        return SESSION_TIMEOUT;
    }
    
    /**
     * @return the zkContainerTypes
     */
    public List<ZookeeperNode> getZkContainerTypes() {
        return zkContainerTypes;
    }

    /**
     * @return the zkContainers
     */
    public List<ZookeeperNode> getZkContainers() {
        return zkContainers;
    }

    /**
     * @return the ZK_ROOT
     */
    public String getZK_ROOT() {
        return ZK_ROOT;
    }


}
