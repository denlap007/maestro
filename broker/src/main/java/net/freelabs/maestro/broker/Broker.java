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
package net.freelabs.maestro.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.freelabs.maestro.core.generated.BusinessContainer;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.generated.WebContainer;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.zookeeper.ConnectionWatcher;
import net.freelabs.maestro.core.zookeeper.ZkNamingServiceNode;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeCreated;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeDataChanged;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 *
 * Class that defines a Broker client to the zookeeper configuration store. Must
 * implement the BrokerInterface.
 */
public abstract class Broker extends ConnectionWatcher implements BrokerInterface, Runnable {

    /**
     * The path of the Container to the zookeeper namespace.
     */
    private final String zkContainerPath;
    /**
     * The path of the Naming service to the zookeeper namespace.
     */
    private final String zkNamingService;
    /**
     * Initial data for the zookeeper Broker node.
     */
    private static final String BROKER_ID = Long.toString(new Random().nextLong());
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Broker.class);
    /**
     * Latch Object with a count of one.
     */
    private final CountDownLatch shutdownSignal = new CountDownLatch(1);
    /**
     * The path of the zNode that indicates the applications shutdown.
     */
    private final String shutdownNode;
    /**
     * The path of the zNode that holds the user configuration for the
     * container.
     */
    private final String userConfNode;

    /**
     * The state of a service required by the container.
     */
    private static enum SERVICE_STATE {
        PROCESSED, NOT_PROCESSED
    };
    /**
     * A map of services (Key) and their STATE (Value).
     */
    private final Map<String, SERVICE_STATE> serviceState = new HashMap<>();
    /**
     * An object to handle execution of operations on another thread.
     */
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    /**
     * The name of the container associated with the broker.
     */
    private String containerName;

    private final String containerType;
    /**
     * The work dir of the Broker, where created files will be stored.
     */
    private final String BROKER_WORK_DIR_PATH;
    /**
     * A Map that holds the configuration of all the dependency containers.
     */
    private final Map<String, Container> servicesConfiguration = new HashMap<>();
    /**
     * The container associated with the broker. Holds the configuration.
     */
    private Container container;
    /**
     * The node of the container service to the naming service.
     */
    private final ZkNamingServiceNode serviceNode;

    /**
     * Constructor
     *
     * @param zkHosts the zookeeper hosts list.
     * @param zkSessionTimeout the client session timeout.
     * @param zkContainerPath the path of the Container to the zookeeper
     * namespace.
     * @param zkNamingService the path of the naming service to the zookeeper
     * namespace.
     * @param shutdownNode the node the signals the shutdown.
     * @param userConfNode the node with the initial container configuration.
     */
    public Broker(String zkHosts, int zkSessionTimeout, String zkContainerPath, String zkNamingService, String shutdownNode, String userConfNode) {
        super(zkHosts, zkSessionTimeout);
        this.zkContainerPath = zkContainerPath;
        this.zkNamingService = zkNamingService;
        this.shutdownNode = shutdownNode;
        this.userConfNode = userConfNode;
        containerName = zkContainerPath.substring(zkContainerPath.lastIndexOf("/") + 1, zkContainerPath.length());
        containerType = zkContainerPath.substring(zkContainerPath.indexOf("/", zkContainerPath.indexOf("/") + 1) + 1, zkContainerPath.lastIndexOf("/"));
        BROKER_WORK_DIR_PATH = BrokerConf.BROKER_BASE_DIR_PATH + File.separator + containerName + "-broker";
        // create a new naming service node
        serviceNode = new ZkNamingServiceNode(zkContainerPath);
        // stores the context data of the particular thread for logging
        MDC.put("containerName", containerName);
    }

    /**
     * Method implementation of run method from Runnable interface.
     */
    @Override
    public void run() {
        bootstrap();
    }

    @Override
    public void bootstrap() {
        // set watch for shutdown zNode
        setShutDownWatch();
        // create container zNode
        createZkNodeEphemeral(zkContainerPath, BROKER_ID.getBytes());
        // set watch for container configuration
        setConfWatch();
        // wait for shutdown
        waitForShutdown();
        // close zk client session
        stop();
    }

    /**
     * Sets a watch on the zookeeper shutdown node. When the shutdown zNode is
     * created execution is terminated.
     */
    private void setShutDownWatch() {
        zk.exists(shutdownNode, shutDownWatcher, shutDownCallback, null);
    }

    /**
     * Callback to be used with {@link #setShutDownWatch() setShutDownWatch()}
     * method.
     */
    private final StatCallback shutDownCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                setShutDownWatch();
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.error("Node exists: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * A watcher to process a watch notification for shutdown node.
     */
    private final Watcher shutDownWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            shutdown();
        }
    };

    /**
     * Creates an EPHEMERAL zNode.
     */
    @Override
    public void createZkNodeEphemeral(String path, byte[] data) {
        zk.create(path, data, OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL, createBrokerCallback, data);
    }

    /**
     * The object to call back with the
     * {@link #createContainer(String) createContainer} method.
     */
    private final StringCallback createBrokerCallback = (int rc, String path, Object ctx, String name) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkContainerNode(path, (byte[]) ctx);
                break;
            case NODEEXISTS:
                LOG.error("Node already exists: " + path);
                break;
            case OK:
                LOG.info("Created zNode: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Checks weather the container is created or not.
     */
    private void checkContainerNode(String path, byte[] data) {
        zk.getData(path, false, checkBrokerCallback, data);
    }

    /**
     * The object to call with the {@link #checkBroker() checkBroker} method.
     */
    private final DataCallback checkBrokerCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkContainerNode(path, (byte[]) ctx);
                break;
            case NONODE:
                createZkNodeEphemeral(path, (byte[]) ctx);
                break;
            case OK:
                String originalData = new String((byte[]) ctx);
                String foundData = new String(data);
                // check if this node is created by this client
                if (foundData.equals(originalData) == true) {
                    LOG.info("Created zNode: " + path);
                } else {
                    LOG.error("Cannot create zNode. Node already exists: " + path);
                }
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Checks if configuration for this container exists and sets a watch.
     */
    private void setConfWatch() {
        zk.exists(userConfNode, setConfWatchWatcher, setConfWatchCallback, null);
    }

    private final AsyncCallback.StatCallback setConfWatchCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                setConfWatch();
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.info("Configuration Node found: " + path);
                // get the configuration from the configuration zNode
                getUserConf();
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * A watcher to process a watch notification for configuration node.
     */
    private final Watcher setConfWatchWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            getUserConf();
        }
    };

    @Override
    public void getUserConf() {
        zk.getData(userConfNode, false, getUserConfCallback, null);
    }

    private final DataCallback getUserConfCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getUserConf();
                break;
            case NONODE:
                LOG.error("Node does NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("ZkNode initialized with configuration: " + path);
                // process configuration
                processUserConf(data);

                executorService.execute(() -> {
                    // create conf file for container associated with the broker
                    createConfFile(container, containerName);
                });

                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Initiates processing of the configuration.
     *
     * @param data
     */
    private void processUserConf(byte[] data) {
        // deserialize configuration 
        container = deserializeContainerConf(data);
        // set data to the container zNode 
        setNodeData(data);
        // register container as service to the naming service
        registerToServices();
        /* query for service - get the configurarion of needed containers
        A service is offered by a container. The needed services are retrieved
        from the current cotnainer configuration from "connectWith" field.
         */
        for (String service : container.getConnectWith()) {
            // set the service status to NOT_RUNNING
            serviceState.put(service, SERVICE_STATE.NOT_PROCESSED);
            // query tha naming service for the required service
            queryForService(service);
        }
    }

    /**
     * Sets data to container's zNode.
     */
    private void setNodeData(byte[] data) {
        zk.setData(zkContainerPath, data, -1, setNodeDataCallback, data);
    }

    /**
     * Callback to be used with {@link #setNodeData(byte[]) setNodeData(byte[])}
     * method.
     */
    private final StatCallback setNodeDataCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                setNodeData((byte[]) ctx);
                break;
            case NONODE:
                LOG.error("Cannot set data to node. NODE DOES NOT EXITST: " + path);
                break;
            case OK:
                LOG.info("Data set to node: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    @Override
    public void registerToServices() {
        // get the service path to the naming service
        String path = resolveServiceName(containerName);
        // serialize the node to byte array
        byte[] data = serializeServiceNode(path, serviceNode);
        // create the zNode of the service to the naming service
        createZkNodeEphemeral(path, data);
    }

    /**
     * Queries the naming service for a service. A service is offered by a
     * container. Every container offering a service registers to the naming
     * service. The name of the container is the name with which the container
     * registers itself to the naming service. The zNode of every service in the
     * naming service holds data. The data is the zNode path of the container to
     * the zookeeper namespace.
     *
     * @param name the name of the service (the container name).
     */
    private void queryForService(String name) {
        LOG.info("Querying for service: " + name);
        // create the path of the service to query the naming service
        String servicePath = resolveServiceName(name);
        // check if service has started
        serviceExists(servicePath);
    }

    /**
     * Checks if service exists.
     *
     * @param servicePath the path of the service under the naming service
     * namespace.
     */
    private void serviceExists(String servicePath) {
        zk.exists(servicePath, serviceExistsWatcher, serviceExistsCallback, null);
    }

    /**
     * Callback to be used in
     * {@link  #serviceExists(java.lang.String) serviceExists(String)} method.
     */
    private final StatCallback serviceExistsCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                serviceExists(path);
                break;
            case NONODE:
                LOG.warn("Service has NOT STARTED yet. Watch set to: " + path);
                break;
            case OK:
                LOG.info("Requested service found: " + path);
                // get data from service zNode. Data resolve service name to container zkPath
                getServiceData(path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }

    };

    /**
     * Watcher to be used in
     * {@link  #serviceExists(java.lang.String) serviceExists(String)} method.
     */
    private final Watcher serviceExistsWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            LOG.info("Watched event: " + event.getType() + " for " + event.getPath() + " ACTIVATED.");
            // get data from container
            getServiceData(event.getPath());
        }
    };

    /**
     * Gets data from the requested service zNode.
     *
     * @param zkPath the path of the container to the zookeeper namespace.
     */
    private void getServiceData(String zkPath) {
        zk.getData(zkPath, false, getServiceDataCallback, null);
    }

    /**
     * The callback to be used with
     * {@link #getServiceData(java.lang.String) getServiceData(String)} method.
     */
    private final DataCallback getServiceDataCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getServiceData(path);
                break;
            case NONODE:
                LOG.error("CANNOT GET DATA from SERVICE. Service node DOES NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("Getting data from service: " + path);
                // process retrieved data from requested service zNode
                processServiceData(data, path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * <p>
     * Processes data retrieved from a service zNode.
     * <p>
     * De-serializes the service node, gets the zNode path of the container
     * offering that service.
     *
     * @param data the data from a service zNode to process.
     * @param path the path of the service zNode.
     */
    private void processServiceData(byte[] data, String path) {
        // de-serialize service node
        ZkNamingServiceNode node = deserializeServiceNode(path, data);
        // get the zNode path of the container of this service
        String containerPath = node.getZkContainerPath();
        // print data
        LOG.info("Service -> Container: {} -> {} ", path, containerPath);
        // GET CONDIGURATION DATA FROM the container of the retrieved zkPath.
        getContainerData(containerPath);
    }

    /**
     * Gets data from the container zNode.
     *
     * @param zkPath the path of the container zNode.
     */
    private void getContainerData(String zkPath) {
        zk.getData(zkPath, false, getContainerDataDataCallback, null);
    }

    /**
     * The callback to be used with
     * {@link #getContainerData(java.lang.String) getContainerData(String)}
     * method.
     */
    private final DataCallback getContainerDataDataCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getContainerData(path);
                break;
            case NONODE:
                LOG.error("CANNOT GET DATA from CONTAINER. Container node DOES NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("Getting data from container: " + path);

                // process retrieved data from requested service zNode
                executorService.execute(() -> {
                    processContainerData(data, path);
                });

                setServiceWatch(path);

                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Processes data retrieved from container zNode.
     *
     * @param data the data of the container zNode.
     * @param path the path of the container zNode.
     */
    private void processContainerData(byte[] data, String path) {
        LOG.info("Processing container data: {}", path);
        // deserialize container data 
        Container serviceContainer = deserializeDependency(path, data);
        // get cotnainer name
        String conName = resolveServicePath(path);
        // save configuration to memory
        servicesConfiguration.put(conName, serviceContainer);
        // save configuration to file named after the container
        createConfFile(serviceContainer, conName);
        // set service status and log the event
        serviceState.replace(conName, SERVICE_STATE.PROCESSED);
        LOG.info("Service: {}\tStatus: {}.", conName, SERVICE_STATE.PROCESSED.toString());
        // check if container is initialized in order to start the main process
        checkMainProcStart();
    }

    /**
     * Checks if all container dependencies are resolved and then initiates the
     * main container process.
     */
    private void checkMainProcStart() {
        if (isContainerInitialized() == true) {
            LOG.info("Container INITIALIZED!");

            // start the main process
            int errCode = startMainProcess();
            // check error code
            if (errCode == 0) {
                LOG.info("Main process INITIALIZED.");
                // change service status to INITIALIZED
                updateServiceStatus();
            } else {
                LOG.error("Main process FAILED to initialize.");
            }
        } else {
            // log services that have not started yet.
            StringBuilder waitingSservices = new StringBuilder();
            for (Map.Entry<String, SERVICE_STATE> entry : serviceState.entrySet()) {
                String key = entry.getKey();
                SERVICE_STATE value = entry.getValue();

                if (value == SERVICE_STATE.NOT_PROCESSED) {
                    waitingSservices.append(key).append(" ");
                }
            }
            LOG.info("Waiting for services: {}", waitingSservices);
        }
    }

    /**
     * Checks if the container is initialized. A container is initialized after
     * it has processed all the necessary configuration for the container and
     * the required services.
     *
     * @return true if the container is initialized.
     */
    private boolean isContainerInitialized() {
        return !serviceState.containsValue(SERVICE_STATE.NOT_PROCESSED);
    }

    /**
     * Starts the main container process.
     *
     * @return the exit code from the started process.
     */
    protected abstract int startMainProcess();

    /**
     * Updates the service status to INITIALIZED.
     */
    private void updateServiceStatus() {
        // get the service path
        String servicePath = resolveServiceName(containerName);
        // log action
        LOG.info("Updating service status to Initialized: {}", servicePath);
        // set the new status for the service
        serviceNode.setStatusInitialized();
        // serialize data
        byte[] updatedData = serializeServiceNode(servicePath, serviceNode);
        // update service node data
        setZNodeData(servicePath, updatedData);
    }

    /**
     * Sets data to a zNode.
     */
    private void setZNodeData(String zNodePath, byte[] data) {
        zk.setData(zNodePath, data, -1, setZNodeDataDataCallback, data);
    }

    /**
     * Callback to be used with {@link #setZNodeData(java.lang.String, byte[])
     * setZNodeData} method.
     */
    private final StatCallback setZNodeDataDataCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                setZNodeData(path, (byte[]) ctx);
                break;
            case NONODE:
                LOG.error("Cannot set data to node. NODE DOES NOT EXITST: " + path);
                break;
            case OK:
                LOG.info("Data set to node: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Registers watches for updates to a service required by the container.
     *
     * @param service a servicer required by the container.
     */
    private void watchServiceUpdates(String serviceName) {
        String servicePath = resolveServiceName(serviceName);
        setServiceWatch(servicePath);
    }

    /**
     * Sets a watch to a service node to monitor for updates.
     *
     * @param servicePath the path of the service to the zookeeper namespace.
     */
    private void setServiceWatch(String servicePath) {
        zk.exists(servicePath, setServiceWatchWatcher, setServiceWatchCallback, null);
    }

    /**
     * Callback to be used in
     * {@link  #setServiceWatch(java.lang.String) setServiceWatch(String)}
     * method.
     */
    private final StatCallback setServiceWatchCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                setServiceWatch(path);
                break;
            case NONODE:
                LOG.error("Service node NOT FOUND: " + path);
                break;
            case OK:
                LOG.info("Setting Watch for updates to: " + path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * Watcher to be used in
     * {@link  #setServiceWatch(java.lang.String) setServiceWatch(String)}
     * method.
     */
    private final Watcher setServiceWatchWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeDataChanged) {
            LOG.info("Watched event: " + event.getType() + " for " + event.getPath() + " ACTIVATED.");
            /**
             *
             * CODE FOR RETRIEVING UPDATED CONFIGURATION
             *
             *
             */

            // RE-SET WATCH TO KEEP MONITORING THE SERVICE
            setServiceWatch(event.getPath());
        }
    };

    /**
     * <p>
     * Resolves a service name to the service path.
     * <p>
     * A service name is a container name. Every running container, after
     * initialization, registers itself to the naming service as a service. The
     * path of the zNode created by this service under the naming service zNode
     * is the service path.
     * <p>
     * The service zNode stores data: the path of the zNode of the corresponding
     * container.
     *
     * @param service the service name.
     * @return the service path to the zookeeper namespace.
     */
    private String resolveServiceName(String service) {
        return zkNamingService.concat("/").concat(service);
    }

    /**
     * Resolves a service/container path to the service/container name.
     *
     * @param path the service/container path to the zookeeper namespace.
     * @return the service name.
     */
    protected String resolveServicePath(String path) {
        return path.substring(path.lastIndexOf("/") + 1, path.length());
    }

    /**
     * Returns the container type.
     *
     * @param path the path of the cotnainer zNode to find the container type.
     * @return the container type.
     */
    private String getContainerType(String path) {
        return path.substring(path.indexOf("/", path.indexOf("/") + 1) + 1, path.lastIndexOf("/"));
    }

    /**
     * <p>
     * Creates a file with the container configuration.
     * <p>
     * Data to be written must be in json format.
     * <p>
     * The file is created to the BROKER_WORK_DIR directory. The full path of
     * the file is derived from the BROKER_WORK_DIR followed by the name of the
     * container.
     *
     * @param data the data to be written.
     * @param fileName the name of the file to hold the data.
     */
    private void createConfFile(Container con, String fileName) {
        // create the final file path
        String path = BROKER_WORK_DIR_PATH + File.separator + fileName;
        // create new file
        File newFile = new File(path);
        // save data to file
        try {
            JsonSerializer.saveToFile(newFile, con);
            // log event
            LOG.info("Created configuration file: {}", path);
        } catch (IOException ex) {
            LOG.error("FAILED to create configuration file: " + ex);
        }
    }

    private void exportEnvVars() {
        try {
            // read configuration file
            FileReader reader = new FileReader(BrokerConf.BROKER_SERVICE_SCRIPT_PATH);
        } catch (FileNotFoundException ex) {
            LOG.error("" + ex);
        }

        // JSONParser jsonParser = new JSONParser();
    }

    /**
     * <p>
     * De-serializes the configuration of the container associated with the
     * broker.
     * <p>
     * The method is inherited by subclasses in order to implement custom
     * functionality according to the container type (web, business, data).
     *
     * @param data the data to be de-serialized.
     * @return a container object with the configuration.
     */
    protected abstract Container deserializeContainerConf(byte[] data);

    private Container deserializeDependency(String path, byte[] data) {
        Container con = null;
        // get the type of the container
        String type = getContainerType(path);
        // de-serialize the container according to the container type
        try {
            if (type.equalsIgnoreCase("WebContainer")) {
                WebContainer webCon = JsonSerializer.deserializeToWebContainer(data);
                con = webCon;
            } else if (type.equalsIgnoreCase("BusinessContainer")) {
                BusinessContainer businessCon = JsonSerializer.deserializeToBusinessContainer(data);
                con = businessCon;
            } else if (type.equalsIgnoreCase("DataContainer")) {
                DataContainer dataCon = JsonSerializer.deserializeToDataContainer(data);
                con = dataCon;
            }

            if (con != null) {
                LOG.info("De-serialized conf of dependency: {}. Printing: \n {}", resolveServicePath(path),
                        JsonSerializer.deserializeToString(data));
            } else {
                LOG.error("De-serialization of dependency {} FAILED", path);
            }
        } catch (IOException ex) {
            LOG.error("De-serialization of dependency FAILED: " + ex);
        }

        return con;
    }

    /**
     * Serializes container configuration.
     *
     * @param conf the container configuration.
     * @return a byte array with the serialzed configuration.
     */
    private byte[] serializeConf(Container con) {
        byte[] data = null;
        try {
            data = JsonSerializer.serialize(con);
            LOG.info("Configuration serialized SUCCESSFULLY!");
        } catch (JsonProcessingException ex) {
            LOG.error("Serialization FAILED: " + ex);
        }
        return data;
    }

    /**
     * Serializes a service node.
     *
     * @param node the service node to serialize.
     * @return a byte array representing the serialized node.
     */
    private byte[] serializeServiceNode(String path, ZkNamingServiceNode node) {
        byte[] data = null;
        try {
            data = JsonSerializer.serializeServiceNode(node);
            LOG.info("Serialized naming service node: {}", path);
        } catch (JsonProcessingException ex) {
            LOG.error("Naming service node Serialization FAILED: " + ex);
        }
        return data;
    }

    /**
     * De-serializes a byte array to a
     * {@link ZkNamingServiceNode ZkNamingServiceNode}.
     *
     * @param data the data to de-serialize.
     * @return a {@link ZkNamingServiceNode ZkNamingServiceNode}.
     */
    private ZkNamingServiceNode deserializeServiceNode(String path, byte[] data) {
        ZkNamingServiceNode node = null;
        try {
            node = JsonSerializer.deserializeServiceNode(data);
            LOG.info("De-serialized naming service node: {}", path);
        } catch (IOException ex) {
            LOG.error("Naming service node de-serialization FAILED!" + ex);
        }
        return node;
    }

    /**
     * Sets a latch to wait for shutdown.
     */
    private void waitForShutdown() {
        try {
            shutdownSignal.await();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Interruption attemplted", ex);
            // set interrupted flag
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Releases the latch to initiate shutdown.
     */
    private void shutdown() {
        shutdownSignal.countDown();
        // shut down then executorService to free resources
        executorService.shutdownNow();
        LOG.info("Initiating Broker shutdown " + zkContainerPath);
    }

    /* ------------------------------- MAIN ------------------------------------
    public static void main(String[] args) throws IOException, InterruptedException {
        // Create and initialize broker
        Broker broker = new Broker(args[0], // zkHosts
                Integer.parseInt(args[1]), // zkSessionTimeout
                args[2], // zkContainerPath
                args[3], // namingService
                args[4], // shutdownNode
                args[5] // userConfNode
        );

        // connect to zookeeper
        broker.connect();
        // create a new thread and start broker
        Thread thread = new Thread(broker, "BrokerThread-" + args[2]);
        thread.start();
    }
     */
}
