/*
 * Copyright (C) 2015-2016 Dionysis Lappas (dio@freelabs.net)
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
package net.freelabs.maestro.core.cmd;

import com.github.dockerjava.api.DockerClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.freelabs.maestro.core.broker.CoreBroker;
import net.freelabs.maestro.core.broker.CoreBusinessBroker;
import net.freelabs.maestro.core.broker.CoreDataBroker;
import net.freelabs.maestro.core.broker.CoreWebBroker;
import net.freelabs.maestro.core.analyze.RestrictionAnalyzer;
import net.freelabs.maestro.core.boot.ProgramConf;
import net.freelabs.maestro.core.xml.XmlProcessor;
import net.freelabs.maestro.core.docker.DockerInitializer;
import net.freelabs.maestro.core.generated.BusinessContainer;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.generated.WebApp;
import net.freelabs.maestro.core.generated.WebContainer;
import net.freelabs.maestro.core.handler.ContainerHandler;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.utils.Utils;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that starts and configures program's components.
 */
public final class StartCmd extends Command {

    /**
     * The master zk process.
     */
    private ZkMaster master;
    /**
     * The thread running the master process
     */
    private Thread masterThread;
    /**
     * A list with all CoreBroker threads created from bootstrap process.
     */
    private final List<Thread> threadList = new ArrayList<>();
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(StartCmd.class);

    /**
     * Constructor.
     *
     * @param cmd the name of the command.
     */
    public StartCmd(String cmd) {
        super(cmd);
    }

    @Override
    public void exec(ProgramConf pConf, String... args) {
        try {
            // unmarshall xml file into a top-level object
            WebApp webApp = unmarshalXml(pConf.getXmlSchemaPath(), pConf.getXmlFilePath());
            // get the name of the webApp
            String webAppName = webApp.getWebAppName();
            // create a handler to query for container information
            ContainerHandler handler = createConHandler(webApp);
            // analyze restrictions and check if apply on schema
            analyzeRestrictions(handler);
            // create zk configuration
            ZkConf zkConf = createZkConf(pConf.getZkHosts(), pConf.getZkSessionTimeout(), handler, webAppName, pConf);
            // initialize zk and start master process
            initZk(zkConf);
            // create a docker client customized for the app
            DockerInitializer appDocker = new DockerInitializer(pConf.getDockerURI());
            DockerClient docker = appDocker.getDockerClient();
            // launch the CoreBrokers to boot containers
            launchBrokers(handler, zkConf, docker);
            // wait until Brokers shutdown and shutdown master
            waitBrokersShutdown();
        } catch (Exception ex) {
            exitProgram(ex);
        }
    }

    /**
     * <p>
     * Waits for all {@link CoreBroker CoreBrokers ) to finish execution. Then
     * initiates master process shutdown.
     * <p>
     * The method blocks.
     */
    private void waitBrokersShutdown() {
        threadList.stream().forEach((t) -> {
            try {
                t.join();
            } catch (InterruptedException ex) {
                LOG.warn("Thread interrupted. Stopping.");
            }
        });
        // shutdown 
        master.shutdownMaster();
        try {
            // wait to finish
            masterThread.join();
        } catch (InterruptedException ex) {
            LOG.warn("Thread interrupted. Stopping.");
        }
        // show the application's deployed name
        master.getDeployedName();
    }

    /**
     * <p>
     * Analyzes the restrictions that must apply on the schema.
     * <p>
     * No circular dependencies are allowed.
     * <p>
     * No duplicate container names are allowed.
     *
     * @param handler object to query for containers.
     */
    private void analyzeRestrictions(ContainerHandler handler) {
        // create analyzer to check restrictions on schema
        RestrictionAnalyzer ra = new RestrictionAnalyzer(handler.listContainers());

        // analyze dependencies
        LOG.info("Analyzing dependencies.");
        // search for circular dependencies
        boolean found = ra.detectCircularDependencies();
        // if circular dependencies found exit
        if (found) {
            errExit();
        }

        // analyze container names 
        LOG.info("Analyzing container names.");
        found = ra.detectDuplicateNames();
        // if duplicate contianer names found exit
        if (found) {
            errExit();
        }
    }

    /**
     * <p>
     * Launches the {@link CoreBroker CoreBrokers} that boot the containers.
     * <p>
     * A {@link Container Container} is obtained from the {@link ContainerHandler
     * ContainerHandler}. A {@link CoreBroker CoreBroker} is created and then
     * connects to zk and starts execution a new thread.
     * <p>
     * The method gets all {@link Container Containers} exhaustively and starts
     * all {@link CoreBroker CoreBrokers} necessary for the program.
     *
     * @param handler object to query for containers.
     * @param zkConf the zk configuration.
     * @param docker a docker client.
     * @throws IOException if connection to zk cannot be established.
     * @throws InterruptedException if thread is interrupted.
     */
    public void launchBrokers(ContainerHandler handler, ZkConf zkConf, DockerClient docker) throws IOException, InterruptedException {
        /*  Get a Container from the container handler. The Container can be of 
            any type. Create the CoreBroker and initialize it. The Broker will 
            connect to zk and then start execution on a new thread.
         */
        while (handler.hasContainers()) {
            Container con = handler.getContainer();
            CoreBroker cb = null;

            if (con instanceof WebContainer) {
                WebContainer webCon = (WebContainer) con;
                cb = new CoreWebBroker(zkConf, webCon, docker, master);
            } else if (con instanceof BusinessContainer) {
                BusinessContainer businessCon = (BusinessContainer) con;
                cb = new CoreBusinessBroker(zkConf, businessCon, docker, master);
            } else if (con instanceof DataContainer) {
                DataContainer dataCon = (DataContainer) con;
                cb = new CoreDataBroker(zkConf, dataCon, docker, master);
            }

            if (cb != null) {
                // create new Thread and start it
                Thread cbThread = new Thread(cb, con.getName() + "-CB-" + "Thread");
                // add to thread list
                threadList.add(cbThread);
                // start thread
                LOG.info("Starting {} CoreBroker.", con.getName());
                cbThread.start();
            } else {
                LOG.error("FAILED to start Broker.");
            }
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
        XmlProcessor proc = new XmlProcessor();
        // Get root Object 
        WebApp webApp = (WebApp) proc.unmarshal("net.freelabs.maestro.core.generated", schemaPath, xmlFilePath);

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
     * {@link net.freelabs.maestro.zookeeper.ZkConfig ZkConf} object. The object
     * stores zk connection configuration along with the zk namespace nodes that
     * need to be created for the application.
     * <p>
     * A handler is passed to retrieve information from/and containers in order
     * to initialize the configuration. The Container Types are the parent nodes
     * of the zk namespace and Containers are the child nodes.
     *
     * @param hosts the host:port zookeeper server list.
     * @param timeout the client session timeout.
     * @param handler a Container handler object to query for container
     * information.
     * @param webAppName the name of the WebApp
     * @return a {@link net.freelabs.maestro.zookeeper.ZkConfig ZkConf} object
     * that holds all the configuration for zookeeper.
     * @throws IOException if serialization of Container object fails.
     */
    public ZkConf createZkConf(String hosts, int timeout, ContainerHandler handler, String webAppName, ProgramConf pConf) throws IOException {
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
        ZkConf zkConf = new ZkConf(webAppName, true, hosts, timeout);

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
            zkConf.initZkContainerType(type);
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
            // generate JSON from container and return the generated JSON as a byte array
            byte[] data = JsonSerializer.serialize(con);
            LOG.info("Container converted to json: " + JsonSerializer.deserializeToString(data));
            // get the name for the child node
            String name = con.getName();
            // get the container type
            String type = Utils.getType(con);
            // initialize child node
            LOG.info("Container name:type: " + name + ":" + type + ". Data size: " + data.length);
            zkConf.initZkContainer(name, type, data);
        }

        // initialize zkConf zknode with data
        byte[] data = JsonSerializer.serialize(zkConf);
        zkConf.getZkConf().setData(data);
        LOG.debug("Printing serialized zkConf: {}", JsonSerializer.deserializeToString(data));
        
        // initialize progConf zknode with data
        data = JsonSerializer.serialize(pConf);
        zkConf.getProgConf().setData(data);
        LOG.debug("Printing serialized pConf: {}", JsonSerializer.deserializeToString(data));

        return zkConf;
    }

    /**
     * <p>
     * Starts a {@link net.freelabs.maestro.core.zookeeper.ZkMaster Master}
     * process that connects to the zookeeper servers, initializes the zookeeper
     * namespace, registers NodeCreated watch events for new zNodes created by a
     * {@link net.freelabs.maestro.broker.Broker Broker}.
     * <p>
     * This method starts a Master process to initialize zk. The master
     * establishes a session with the zookeeper servers. After the session is
     * established, the Master is created, the watched event is processed and
     * bootstraps the creation of the namespace from the
     * {@link net.freelabs.maestro.zookeeper.ZkConfig ZkConf} object.
     *
     * @param zkConf an object with the zk configuration.
     * @throws InterruptedException if thread is interrupted.
     * @throws IOException in cases of network failure.
     */
    public void initZk(ZkConf zkConf) throws InterruptedException, IOException {
        // create a master object and initialize it with zk configuration
        master = new ZkMaster(zkConf);
        // Create a new thread to run the master 
        masterThread = new Thread(master, "Master-thread");
        // start the master process
        masterThread.start();
        // wait for initialization
        LOG.info("WAITING FOR MASTER INITIALIZATION");
        // check initialization
        if (!master.isMasterInitialized()) {
            errExit();
        }
    }

    /**
     * Terminates the program due to some error printing the cause.
     */
    private void exitProgram(Exception ex) {
        LOG.error("Something went wrong: ", ex);
        if (master != null) {
            master.shutdown();
        }
        errExit();
    }

    /**
     * Terminates the program due to some error.
     */
    private void errExit() {
        LOG.error("The program will exit!");

        System.exit(1);
    }
}
