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
package net.freelabs.maestro.core.zookeeper;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Class that holds all the zookeeper pre-configuration.
 */
public final class ZkConfig {

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
    private final List<ZkNode> zkContainerTypes = new ArrayList<>();
    /**
     * A list with the zookeeper container nodes.
     */
    private final HashMap<String, ZkNode> zkContainers = new HashMap<>();
    /**
     * The naming service zNode path in the zookeeper namespace.
     */
    private final String namingServicePath;
    /**
     * The master zNode path in the zookeeper namespace.
     */
    private final String masterPath;
    /**
     * The shutdown zNode path in the zookeeper namespace. This zNode is used to
     * initiate the application shutdown process.
     */
    private final String shutDownPath;
    /**
     * The initial configuration zNode path in the zookeepers namespace. This
     * zNode is used to hold the initial configuration of the container. When a
     * container is started it will read the configuration and then delete the
     * zNode.
     */
    private final String userConfPath;

    /**
     * Constructor.
     *
     * @param hosts the list of zookeeper hosts. Must follow the format: 
     * <pre> HOST1_IP:PORT, HOST2_IP:PORT, ...</pre>
     *
     * @param SESSION_TIMEOUT the time until session is closed if the client
     * hasn't contacted the server.
     * @param ZK_ROOT the root of the zookeeper namespace where application's
     * node hierarchy will be created.
     */
    public ZkConfig(String hosts, int SESSION_TIMEOUT, String ZK_ROOT) {
        this.hosts = hosts;
        this.SESSION_TIMEOUT = SESSION_TIMEOUT;
        this.ZK_ROOT = "/" + ZK_ROOT;
        namingServicePath = this.ZK_ROOT + "/services";
        masterPath = this.ZK_ROOT + "/master";
        shutDownPath = this.ZK_ROOT + "/shutdown";
        userConfPath = this.ZK_ROOT + "/conf";
    }

    /**
     * Initializes a parent (@link ZkNode). The zookeeper path of the parent is
     * derived from two components: the zookeeper root + the type argument.
     *
     * @param type container type.
     * @param data zkNode's data.
     */
    public void initZkContainerTypes(String type, byte[] data) {
        // create node's path
        String path = getZK_ROOT() + "/" + type;
        // craete node's name
        String name = "/" + type;
        // create a new zk node object
        ZkNode zkNode = new ZkNode(path, data, name, null);
        // add to list
        zkContainerTypes.add(zkNode);
    }

    /**
     * Initializes a child (@link ZkNode). The zookeeper path of the child is
     * derived from three components: the zookeeper root + the type argument +
     * name argument.
     *
     * @param name the name of a container.
     * @param type the type of a container.
     * @param data the data of the zkNode.
     */
    public void initZkContainers(String name, String type, byte[] data) {
        // create node's path
        String path = getZK_ROOT() + "/" + type + "/" + name;
        // craete node's name
        String zkName = "/" + name;
        // create a new zk node object
        String zkConfNode = userConfPath + "/" + name;
        ZkNode zkNode = new ZkNode(path, data, zkName, zkConfNode);
        // add to list
        zkContainers.put(name, zkNode);
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
    public List<ZkNode> getZkContainerTypes() {
        return zkContainerTypes;
    }

    /**
     * @return the zkContainers
     */
    public HashMap<String, ZkNode> getZkContainers() {
        return zkContainers;
    }

    /**
     * @return the ZK_ROOT
     */
    public String getZK_ROOT() {
        return ZK_ROOT;
    }

    /**
     * @return the namingServicePath
     */
    public String getNamingServicePath() {
        return namingServicePath;
    }

    /**
     * @return the masterPath
     */
    public String getMasterPath() {
        return masterPath;
    }

    /**
     * @return the shutDownPath
     */
    public String getShutDownPath() {
        return shutDownPath;
    }

    /**
     * @return the userConfPath
     */
    public String getUserConfPath() {
        return userConfPath;
    }

}
