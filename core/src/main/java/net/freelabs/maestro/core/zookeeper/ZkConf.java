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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class ZkConf {

    /**
     * The root zkNode for the application.
     */
    private ZkNode root;
    /**
     * The naming service zkNode for the application.
     */
    private ZkNode services;
    /**
     * The container description zkNode for the application. Under this node
     * will be saved all container descriptions.
     */
    private ZkNode conDesc;
    /**
     * The shutdown node for the application. When created indicates program
     * shutdown.
     */
    private ZkNode shutdown;
    /**
     * The master zkNode for the application. Under this node will be registered
     * {@link #zkConf zkConf}, {@link #progConf progConf}, {@link appConf appConf}
     * nodes.
     */
    private ZkNode master;
    /**
     * The zkNode with all the configuration regarding zookeeper service and
     * application deployment to zookeeper service.
     */
    private ZkNode zkConf;
    /**
     * The zkNode with the program's configuration.
     */
    private ZkNode progConf;
    /**
     * The zkNodes holding info about the container types.
     */
    private List<ZkNode> containerTypes;
    /**
     * The zkNodes holding info about containers.
     */
    private Map<String, ZkNode> containers;
    /**
     * The namespace of the application to the zookeeper service.
     */
    private List<ZkNode> zkAppNamespace;
    /**
     * The client configuration that connects to the zookeeper service.
     */
    @JsonIgnore
    private ZkSrvConf zkSrvConf;
    /**
     * An id used as data for nodes without data. Also, this is the suffic to
     * the zk root node for the application.
     */
    private String strId;

    /**
     * Constructor.
     *
     * @param root the root application node to the zookeeper namespace.
     * @param hosts the list of zookeeper hosts. Must follow the format: 
     * <pre> HOST1_IP:PORT,HOST2_IP:PORT, ...</pre>
     *
     * @param timeout the time until session is closed if the client hasn't
     * contacted the server.
     * @param suffixed determines weather to apply an 8-digit suffix to the
     * application root zNode.
     */
    public ZkConf(
            String root,
            boolean suffixed,
            String hosts,
            int timeout) {
        // create namespace list
        zkAppNamespace = new ArrayList<>();
        // create root zkNode
        // create ID for root node, an 8-digit positive number zero padded
        int numId;

        String name;
        if (suffixed) {
            numId = (new Random().nextInt(90000000) + 10000000);
            strId = String.format("%08d", numId);
            name = root + "-" + strId;
        } else {
            name = root;
            String[] tokens = name.split("-");
            if (tokens != null) {
                if (tokens.length == 2) {
                    strId = tokens[1];
                } else {
                    strId = "0";
                }
            } else {
                strId = "0";
            }
        }
        String rootPath = "/" + name;
        this.root = new ZkNode(rootPath, strId.getBytes(), name, "");
        zkAppNamespace.add(this.root);
        // create services zkNode
        String path = rootPath + "/services";
        name = "services";
        services = new ZkNode(path, strId.getBytes(), name, "");
        zkAppNamespace.add(services);
        // create zknode for container descriptions
        path = rootPath + "/conf";
        name = "conf";
        conDesc = new ZkNode(path, strId.getBytes(), name, "");
        zkAppNamespace.add(conDesc);
        // create shutdown zkNode
        path = rootPath + "/shutdown";
        name = "shutdown";
        shutdown = new ZkNode(path, strId.getBytes(), name, "");
        // create master zkNode
        path = rootPath + "/master";
        name = "master";
        master = new ZkNode(path, strId.getBytes(), name, "");
        zkAppNamespace.add(master);
        // create zkNode for zookeeper configuration
        path = master.getPath() + "/zkConf";
        name = "zkConf";
        zkConf = new ZkNode(path, strId.getBytes(), name, "");
        zkAppNamespace.add(zkConf);
        // create zkNode for program configuration
        path = master.getPath() + "/progConf";
        name = "progConf";
        progConf = new ZkNode(path, strId.getBytes(), name, "");
        zkAppNamespace.add(progConf);
        // create Lists-Maps
        containerTypes = new ArrayList<>();
        containers = new HashMap<>();
        // initialize client configuration
        zkSrvConf = new ZkSrvConf(hosts, timeout);
    }

    /**
     * Default Constructor FOR JACKSON COMPATIBILITY.
     */
    public ZkConf(){
        
    }
    
    /**
     * Initializes a (@link ZkNode) for a container type. The zookeeper path is
     * derived from two components: the zookeeper root + the type argument.
     *
     * @param type container type.
     */
    public void initZkContainerType(String type) {
        // create node's path
        String path = root.getPath() + "/" + type;
        // craete node's name
        String name = "/" + type;
        // create a new zk node object 
        ZkNode zkNode = new ZkNode(path, strId.getBytes(), name, "");
        // add to list
        containerTypes.add(zkNode);
        zkAppNamespace.add(zkNode);
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

    public List<ZkNode> getContainerTypes() {
        return containerTypes;
    }

    public Map<String, ZkNode> getContainers() {
        return containers;
    }

    public List<ZkNode> getZkAppNamespace() {
        return zkAppNamespace;
    }

    public ZkSrvConf getZkSrvConf() {
        return zkSrvConf;
    }
}
