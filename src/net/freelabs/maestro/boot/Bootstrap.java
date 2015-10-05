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
package net.freelabs.maestro.boot;

import java.io.IOException;
import net.freelabs.maestro.conf.ConfProcessor;
import net.freelabs.maestro.generated.Container;
import net.freelabs.maestro.generated.WebApp;
import net.freelabs.maestro.handler.ContainerHandler;
import net.freelabs.maestro.serialize.Serializer;
import net.freelabs.maestro.utils.Utils;
import net.freelabs.maestro.zookeeper.ZkMaster;
import net.freelabs.maestro.zookeeper.ZkNamingService;
import net.freelabs.maestro.zookeeper.ZookeeperConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that starts and configures program's components.
 */
public final class Bootstrap {

    /**
     * The master zk process.
     */
    private ZkMaster master;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Bootstrap.class);

    public void boot() {
        try {
            // Read configuration
            ProgramConf conf = new ProgramConf();
            // unmarshall xml file into a top-level object
            WebApp webApp = unmarshalXml(conf.getXmlSchemaPath(), conf.getXmlFilePath());
            // get the name of the webApp
            String webAppName = webApp.getWebAppName();
            // create a handler to query container information
            ContainerHandler handler = createConHandler(webApp);
            // create zk configuration
            ZookeeperConfig zkConf = createZkConf(conf.getZkHosts(), conf.getZkSessionTimeout(), handler, webAppName);
            // initialize zk and start master process
            initZk(zkConf);
        } catch (Exception ex) {
            exitProgram(ex);
        }

    }

    /**
     * Unmarshals an xml file.
     *
     * @param schemaPath the path to the xml schema.
     * @param xmlFilePath the path to the xml file.
     * @return an object of the top-level element.
     */
    public WebApp unmarshalXml(String schemaPath, String xmlFilePath) {
        // create an object that processes initial configuration
        ConfProcessor proc = new ConfProcessor();
        // Get root Object 
        WebApp webApp = (WebApp) proc.unmarshal("net.freelabs.maestro.generated", schemaPath, xmlFilePath);

        return webApp;
    }

    /**
     * <p>
     * Returns a container handler object.
     * <p>
     * A container handler is queried from other objects to obtain information
     * about the containers and/or the containers themselves.
     *
     * @param webApp an object of class WebApp. This object represents the
     * top-level element from an unmarshalled xml file.
     * @return a container handler.
     */
    public ContainerHandler createConHandler(WebApp webApp) {
        // Get a handler for containers
        ContainerHandler handler = new ContainerHandler(webApp.getContainers());
        return handler;
    }

    /**
     * <p>
     * Creates all the necessary zookeeper configuration for the program to
     * start.
     * <p>
     * The Zookeeper configuration is stored at a
     * {@link net.freelabs.maestro.zookeeper.ZookeeperConfig ZookeeperConfig}
     * object. The object stores zk connection configuration along with the zk
     * namespace nodes that need to be created for the application.
     * <p>
     * A handler is passed to retrieve information from/and containers in order
     * to initialize the configuration. The Container Types are the parent nodes
     * of the zk namespace and Containers are the child nodes.
     *
     * @param hosts the host:port zookeeper server list.
     * @param timeout the client session timeout.
     * @param handler a Container handler object to query for container
     * information.
     * @return a
     * {@link net.freelabs.maestro.zookeeper.ZookeeperConfig ZookeeperConfig}
     * object that holds all the configuration for zookeeper.
     * @throws IOException if serialization of Container object fails.
     */
    public ZookeeperConfig createZkConf(String hosts, int timeout, ContainerHandler handler, String webAppName) throws IOException {
        /*
         Create a zookeeper configuration object. This object holds all the
         necessary configuration information needed for zookeeper to boostrap-
         initialize and for the program to work. Among others, it holds the host:port 
         connection server list, client session timeout, paths and data of parent 
         zookeeper nodes that correspond to the first nodes created -based to the 
         different Container Types (e.g. web, business, data e.t.c.) declared, 
         paths and data of children zookeeper nodes that correspond to the children 
         of parent nodes based on declared Containers e.t.c.
         */
        ZookeeperConfig zkConf = new ZookeeperConfig(hosts, timeout, webAppName);

        /* 
         Initialize zookeeper parent nodes. The zookeeper namespace has an 
         hierarchical structure of nodes like a file system, called zNodes. ZNodes
         are referenced by paths and may store data.  
         The parent nodes are created on the root of the zookeeper namespace. Each
         parent node's path is construted using a Container Type (e.g. /web). The 
         parent nodes usually have children. The children correspond to Containers.    
        
         We query the Container Handler for the available Container Types and then
         we initialize the zookeeper parent nodes. We store no data to these nodes.
         Parent nodes are Persistent zNodes.
         */
        for (String type : handler.getContainerTypes()) {
            LOG.info("Container type: " + type);
            zkConf.initZkContainerTypes(type, new byte[0]);
        }

        /*
         Initialize zookeeper child nodes. Every child node (Container) belongs to
         a parent node (Container Type). Child nodes are Ephemeral zNodes and cannot 
         have children of their own.
        
         We query the Container Handler for the available Containers and then we
         initialize the zookeeper child nodes. The data of a child node is a 
         serialized Container object.
         */
        for (Container con : handler.listContainers()) {
            // get the data for the child node
            byte[] data = Serializer.serialize(con);
            // get the name for the child node
            String name = con.getName();
            // get the container type
            String type = Utils.getType(con);
            // initialize child node
            LOG.info("Container name,type: " + name + ", " + type);
            zkConf.initZkContainers(name, type, data);
        }

        return zkConf;
    }

    /**
     * <p>
     * Starts a {@link net.freelabs.maestro.zookeeper.ZkMaster Master} process
     * that connects to the zookeeper servers, initializes the zookeeper
     * namespace, registers NodeCreated watch events for new zNodes created by a
     * {@link net.freelabs.maestro.broker.Broker Broker} and when the new zNodes
     * are created, initializes them with data.
     * <p>
     * This method starts a Master process to initialize zk. The master
     * establishes a session with the zookeeper servers. After the session is
     * established, the Master is created, the watched event is processed and 
     * bootstraps the creation of the namespace from the
     * {@link net.freelabs.maestro.zookeeper.ZookeeperConfig ZookeeperConfig}
     * object.
     *
     * @param zkConf an object with the zk configuration.
     * @throws InterruptedException if thread is interrupted.
     * @throws IOException in cases of network failure.
     */
    public void initZk(ZookeeperConfig zkConf) throws InterruptedException, IOException {
        // create naming service
        ZkNamingService services = new ZkNamingService(zkConf);
        // create a session and connect to zookeeper
        services.connect();
        // Create a new thread to run the naming service
        Thread servicesThread = new Thread(services, "namingServiceThread");
        // start the naming service
        servicesThread.start();
        
        
        // Create master
        master = new ZkMaster(zkConf);
        // create a session
        master.connect();
        // Create a new thread to run the master code
        Thread masterThread = new Thread(master, "masterThread");
        // create and run master zkNode
        masterThread.start();
    }

    /**
     * Terminates the program due to some error with an error exit code.
     */
    private void exitProgram(Exception ex) {
        LOG.error("Something went wrong! The program will exit!", ex);
        try {
            master.cleanUp();
            master.stop();
        } catch (InterruptedException ex1) {
            LOG.error("Thread Interrupted", ex1);
        }
        System.exit(1);
    }

    // ------------------------------ MAIN -------------------------------------
    public static void main(String[] args) throws InterruptedException {

        // create the bootstrap object to boot the program
        Bootstrap starter = new Bootstrap();

        // boot the program
        starter.boot();
    }

}
