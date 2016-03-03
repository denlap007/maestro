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
package net.freelabs.maestro.core.zookeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 *
 * Class that provides methods to access configuration information of a web
 * application regarding its interaction with zookeeper service.
 * <p>
 * The zookeeper namespace of the app is defined along with data for the nodes.
 */
public final class ZkAppConf {

    /**
     * The root zkNode for the application.
     */
    private final ZkNode root;
    /**
     * The naming service zkNode for the application.
     */
    private final ZkNode services;
    /**
     * The container description zkNode for the application. Under this node
     * will be saved all container descriptions.
     */
    private final ZkNode conDesc;
    /**
     * The shutdown node for the application. When created indicates program
     * shutdown.
     */
    private final ZkNode shutdown;
    /**
     * The master zkNode for the application. Under this node will be registered
     * {@link #zkConf zkConf}, {@link #progConf progConf}, {@link appConf appConf}
     * nodes.
     */
    private final ZkNode master;
    /**
     * The zkNode with all the configuration regarding zookeeper service and
     * application deployment to zookeeper service.
     */
    private final ZkNode zkConf;
    /**
     * The zkNode with the program's configuration.
     */
    private final ZkNode progConf;
    /**
     * The zkNode with the application configuration.
     */
    private final ZkNode appConf;
    /**
     * The zkNodes holding info about the container types.
     */
    private final List<ZkNode> containerTypes;
    /**
     * The zkNodes holding info about containers.
     */
    private final Map<String, ZkNode> containers;

    /**
     * Constructor.
     *
     * @param root the root application node to the zookeeper namespace.
     */
    public ZkAppConf(String root) {
        // create root zkNode
        // create ID for root node, an 8-digit positive number zero padded
        int numId = (new Random().nextInt(90000000) + 10000000);
        String strId = String.format("%08d", numId);
        String name = root + "-" + strId;
        String rootPath = "/" + name;
        this.root = new ZkNode(rootPath, null, name, null);
        // create services zkNode
        String path = rootPath + "/services";
        name = "services";
        services = new ZkNode(path, null, name, null);
        // create zknode for container descriptions
        path = rootPath + "/conf";
        name = "conf";
        conDesc = new ZkNode(path, null, name, null);
        // create shutdown zkNode
        path = rootPath + "/shutdown";
        name = "shutdown";
        shutdown = new ZkNode(path, null, name, null);
        // create master zkNode
        path = rootPath + "/master";
        name = "master";
        master = new ZkNode(path, null, name, null);
        // create zkNode for zookeeper configuration
        path = master.getPath() + "/zkConf";
        name = "zkConf";
        zkConf = new ZkNode(path, null, name, null);
        // create zkNode for program configuration
        path = master.getPath() + "/progConf";
        name = "progConf";
        progConf = new ZkNode(path, null, name, null);
        // create zkNode for appplication configuration
        path = master.getPath() + "/appConf";
        name = "appConf";
        appConf = new ZkNode(path, null, name, null);
        // create Lists-Maps
        containerTypes = new ArrayList<>();
        containers = new HashMap<>();
    }

    /**
     * Initializes a (@link ZkNode) for a container type. The zookeeper path is
     * derived from two components: the zookeeper root + the type argument.
     *
     * @param type container type.
     * @param data zkNode's data.
     */
    public void initZkContainerType(String type, byte[] data) {
        // create node's path
        String path = root.getPath() + "/" + type;
        // craete node's name
        String name = "/" + type;
        // create a new zk node object 
        ZkNode zkNode = new ZkNode(path, data, name, null);
        // add to list
        containerTypes.add(zkNode);
    }

    /**
     * Initializes a (@link ZkNode) for a container. The zookeeper path is
     * derived from three components: the zookeeper root + the type argument +
     * name argument.
     *
     * @param name the name of a container.
     * @param type the type of a container.
     * @param data the data of the zkNode.
     */
    public void initZkContainer(String name, String type, byte[] data) {
        // create node's path
        String nodePath = root.getPath() + "/" + type + "/" + name;
        // craete node's name
        String nodeName = name;
        // create the path where the container's configuration will be stored to zk
        String conConfPath = conDesc.getPath() + "/" + name;
        // create a new zk node object
        ZkNode zkNode = new ZkNode(nodePath, data, nodeName, conConfPath);
        // add to list
        containers.put(name, zkNode);
    }

    /**
     * Initializes {@link #zkConf zkConf} with data.
     *
     * @param data
     */
    public void initZkConf(byte[] data) {
        // add data to zkConf node
        zkConf.setData(data);
    }

    // Getters 
    public ZkNode getRoot() {
        return root;
    }

    public ZkNode getServices() {
        return services;
    }

    public ZkNode getConDesc() {
        return conDesc;
    }

    public ZkNode getShutdown() {
        return shutdown;
    }

    public ZkNode getMaster() {
        return master;
    }

    public ZkNode getZkConf() {
        return zkConf;
    }

    public ZkNode getProgConf() {
        return progConf;
    }

    public ZkNode getAppConf() {
        return appConf;
    }

    public List<ZkNode> getContainerTypes() {
        return containerTypes;
    }

    public Map<String, ZkNode> getContainers() {
        return containers;
    }
}
