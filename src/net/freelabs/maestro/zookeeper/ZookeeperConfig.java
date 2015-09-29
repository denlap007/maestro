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
     * The list of zookeepers hosts
     */
    private final String hosts;
    /**
     * The zookeeper client session time out
     */
    private final int SESSION_TIMEOUT;
    /**
     * The default charset to encode data
     */
    private static final Charset CHARSET = Charset.forName("UTF-8");
    /**
     * The zookeeper root for the namespace.
     */
    private static final String ROOT = "/";

    /**
     * A list with the zookeeper parent nodes.
     */
    private List<ZookeeperNode> zkParents = new ArrayList<>();
    private List<ZookeeperNode> zkChildren = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param hosts the list of zookeeper hosts. Must follow the format: 
     * <pre> HOST1_IP:PORT, HOST2_IP:PORT, ...</pre>
     *
     * @param SESSION_TIMEOUT the time until session is closed if the client
     * hasn't contacted the server.
     */
    public ZookeeperConfig(String hosts, int SESSION_TIMEOUT) {
        this.hosts = hosts;
        this.SESSION_TIMEOUT = SESSION_TIMEOUT;
    }

    /**
     * Initializes a parent (@link ZookeeperNode). The zookeeper path of the parent is
     * derived from two components: the zookeeper root + the type argument.
     * @param type container type.
     * @param data zkNode's data.
     */
    public void initParentNode(String type, byte[] data) {
        // create node's path
        String path = ROOT + type;
        // create a new zk node object
        ZookeeperNode zkNode = new ZookeeperNode(path, data);
        // add to list
        zkParents.add(zkNode);
    }

    /**
     * Initializes a child (@link ZookeeperNode). The zookeeper path of the child is
     * derived from three components: the zookeeper root + the type argument + 
     * name argument.
     * @param name the name of a container.
     * @param type the type of a container.
     * @param data the data of the zkNode.
     */
    public void initChildNode(String name, String type, byte[] data) {
        // create node's path
        String path = ROOT + type + ROOT + name;
        // create a new zk node object
        ZookeeperNode zkNode = new ZookeeperNode(path, data);
        // add to list
        zkChildren.add(zkNode);
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
     * @return the zkParents
     */
    public List<ZookeeperNode> getZkParents() {
        return zkParents;
    }

    /**
     * @return the zkChildren
     */
    public List<ZookeeperNode> getZkChildren() {
        return zkChildren;
    }

    
    
    
    
    /* ------------------------------ TEST -----------------------------------*/
    public static void main(String[] args) {
        String hosts = "127.0.0.1:2181";
        int timeout = 5000;
        // create zkConf
        ZookeeperConfig zkConf = new ZookeeperConfig(hosts, timeout);
        // create parent nodes
        String p1 = "/web";
        String p1Data = "web";
        String p2 = "/business";
        String p2Data = "business";
        String p3 = "/data";
        String p3Data = "data";


    }

}
