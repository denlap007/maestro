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
package net.freelabs.maestro.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
 * Class that defines a Broker client to the zookeeper configuration store.
 */
public abstract class Broker extends ConnectionWatcher implements Runnable {

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
     * The path of the zNode that holds the initial configuration for the
     * container.
     */
    private final String conConfNode;

    /**
     * The status of a service-dependency, required by the container.
     */
    private static enum DEPENDENCY_STATUS {
        PROCESSED, NOT_PROCESSED
    };
    /**
     * A map of services (Key) and their STATUS (Value).
     */
    private final Map<String, DEPENDENCY_STATUS> dependenciesState = new HashMap<>();
    /**
     * An object to handle execution of operations on another thread.
     */
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    /**
     * The name of the container associated with the broker.
     */
    private String containerName;
    /**
     * The type of the container.
     */
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
     * @param conConfNode the node with the initial container configuration.
     */
    public Broker(String zkHosts, int zkSessionTimeout, String zkContainerPath, String zkNamingService, String shutdownNode, String conConfNode) {
        super(zkHosts, zkSessionTimeout);
        this.zkContainerPath = zkContainerPath;
        this.zkNamingService = zkNamingService;
        this.shutdownNode = shutdownNode;
        this.conConfNode = conConfNode;
        containerName = resolveServicePath(zkContainerPath);
        containerType = getContainerType(zkContainerPath);
        BROKER_WORK_DIR_PATH = BrokerConf.BROKER_BASE_DIR_PATH + File.separator + containerName + "-broker";
        // create a new naming service node
        serviceNode = new ZkNamingServiceNode(zkContainerPath);
        // stores the context data of the particular thread for logging
        MDC.put("id", containerName);
    }

    @Override
    public void run() {
        bootstrap();
    }

    /*
     * *************************************************************************
     * BOOTSTRAPPING
     * **************************************************************************
     */
    /**
     * Bootstraps the broker.
     */
    public void bootstrap() {
        // set watch for shutdown zNode
        setShutDownWatch();
        // create container zNode
        createZkNodeEphemeral(zkContainerPath, BROKER_ID.getBytes());
        // set watch for the container description
        waitForConDescription();
        // wait for shutdown
        waitForShutdown();
        // close zk client session
        stop();
    }

    /*
     * *************************************************************************
     * INITIALIZATION
     * **************************************************************************
     */
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
     *
     * @param path the path of the zNode to the zookeeper namespace.
     * @param data the data of the zNode.
     */
    public void createZkNodeEphemeral(String path, byte[] data) {
        zk.create(path, data, OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL, createZkNodeEphemeralCallback, data);
    }

    /**
     * The object to call back with {@link #createZkNodeEphemeral(java.lang.String, byte[])
     * createZkNodeEphemeral} method.
     */
    private final StringCallback createZkNodeEphemeralCallback = (int rc, String path, Object ctx, String name) -> {
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
     * Checks weather the container zNode was created or not.
     */
    private void checkContainerNode(String path, byte[] data) {
        zk.getData(path, false, checkContainerNodeCallback, data);
    }

    /**
     * The object to call back with {@link #checkContainerNode(java.lang.String, byte[])
     * checkContainerNode} method.
     */
    private final DataCallback checkContainerNodeCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
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
                // check if this zNode is created by this client
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
     * Checks if the container description for this container exists and sets a
     * watch.
     */
    private void waitForConDescription() {
        zk.exists(conConfNode, waitForConDescriptionWatcher, waitForConDescriptionCallback, null);
    }

    /**
     * The object to call back with
     * {@link #waitForConDescription() waitForConDescription} method.
     */
    private final AsyncCallback.StatCallback waitForConDescriptionCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                waitForConDescription();
                break;
            case NONODE:
                LOG.info("Watch registered on: " + path);
                break;
            case OK:
                LOG.info("Configuration Node found: " + path);
                // get the description from the configuration zNode
                getConDescription();
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    /**
     * A watcher to process a watch notification for configuration node.
     */
    private final Watcher waitForConDescriptionWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            getConDescription();
        }
    };

    /**
     * Gets the container description.
     */
    public void getConDescription() {
        zk.getData(conConfNode, false, getUserConfCallback, null);
    }

    /**
     * The object to call back with
     * {@link #getConDescription() getConDescription} method.
     */
    private final DataCallback getUserConfCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getConDescription();
                break;
            case NONODE:
                LOG.error("Node does NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("ZkNode initialized with configuration: " + path);
                // process container description
                processConDescription(data);

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
     * Initiates processing of the container description.
     *
     * @param data a serialized {@link Container Container}.
     */
    private void processConDescription(byte[] data) {
        // deserialize container 
        container = deserializeConType(data);
        // set data to the container zNode 
        setNodeData(data);
        // register container as service to the naming service
        registerToServices();
        /* query for service - get the configurarion of needed containers
        A service is offered by a container. The needed services are retrieved
        from the current cotnainer configuration from "connectWith" field.
         */
        for (String service : container.getConnectWith()) {
            // set the service-dependency status to NOT_PROCESSED
            dependenciesState.put(service, DEPENDENCY_STATUS.NOT_PROCESSED);
            // query tha naming service for the required service
            queryForService(service);
        }
    }

    /**
     * Sets data to the container's zNode.
     */
    private void setNodeData(byte[] data) {
        zk.setData(zkContainerPath, data, -1, setNodeDataCallback, data);
    }

    /**
     * Callback to be used with {@link #setNodeData(byte[]) setNodeDate} method.
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

    /**
     * <p>
     * Registers the container as a service to the naming service.
     * <p>
     * The container name is also the name of the service. Every registered
     * service is represented by a service zNode. A service zNode is a
     * serialized {@link ZkNamingServiceNode ZkNamingServiceNode} object.
     * <p>
     * The service node contains the zNode path of the container offering the
     * service along with the status of the service (initialized or not).
     */
    public void registerToServices() {
        // create the service path for the naming service
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
     * the zookeeper namespace and the status of the service (initialized or
     * not).
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
     * Callback to be used with
     * {@link  #serviceExists(java.lang.String) serviceExists} method.
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
                // get data from service zNode.
                getServiceData(path);
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }

    };

    /**
     * Watcher to be used with
     * {@link  #serviceExists(java.lang.String) serviceExists} method.
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
        getConData(containerPath);
    }

    /**
     * Gets data from the container zNode.
     *
     * @param zkPath the path of the container zNode.
     */
    private void getConData(String zkPath) {
        zk.getData(zkPath, false, getConDataDataCallback, null);
    }

    /**
     * The callback to be used with
     * {@link #getConData(java.lang.String) getConData(String)} method.
     */
    private final DataCallback getConDataDataCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getConData(path);
                break;
            case NONODE:
                LOG.error("CANNOT GET DATA from CONTAINER. Container node DOES NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("Getting data from container: " + path);

                // process retrieved data from requested service zNode
                executorService.execute(() -> {
                    processConData(data, path);
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
    private void processConData(byte[] data, String path) {
        LOG.info("Processing container data: {}", path);
        // deserialize container data 
        Container serviceContainer = deserializeDependency(path, data);
        // get cotnainer name
        String conName = resolveServicePath(path);
        // save configuration to memory
        servicesConfiguration.put(path, serviceContainer);
        // save configuration to file named after the container
        createConfFile(serviceContainer, conName);
        // set service-dependency status and log the event
        dependenciesState.replace(conName, DEPENDENCY_STATUS.PROCESSED);
        LOG.info("Service: {}\tStatus: {}.", conName, DEPENDENCY_STATUS.PROCESSED.toString());
        // check if container is initialized in order to start the entrypoint process
        checkInitialization();
    }

    /**
     * *************************************************************************
     * PROCESS HANDLING
     * *************************************************************************
     */
    /**
     * Checks if all container dependencies are resolved and if so initiates the
     * entrypoint container process.
     */
    private void checkInitialization() {
        // if container is initialized
        if (isContainerInitialized()) {
            // start the entryoint process
            startEntrypointProcess();
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
        // check if there are not processes services and set value accoordingly
        boolean initialized = !dependenciesState.containsValue(DEPENDENCY_STATUS.NOT_PROCESSED);
        // print messages according to state
        if (initialized) {
            LOG.info("Container INITIALIZED!");
        } else {
            // log services that have not started yet.
            StringBuilder waitingServices = new StringBuilder();
            for (Map.Entry<String, DEPENDENCY_STATUS> entry : dependenciesState.entrySet()) {
                String key = entry.getKey();
                DEPENDENCY_STATUS value = entry.getValue();

                if (value == DEPENDENCY_STATUS.NOT_PROCESSED) {
                    waitingServices.append(key).append(" ");
                }
            }
            LOG.info("Waiting for services: {}", waitingServices);
        }

        return initialized;
    }

    /**
     * Starts the main container process.
     *
     */
    private void startEntrypointProcess() {
        // handle the entrypoint processing
        EntrypointHandler entryHandler = handleEntrypoint();
        // handle the interaction with the new process
        handleProcess(entryHandler);
    }

    /**
     * <p>
     * Handles the interaction with the entrypoint script.
     * <p>
     * The method creates an {@link EntrypointHandler EntrypointHandler} that
     * loads the entrypoint and processes it accordingly. Also, sets cmd and
     * arguments specified to be executed by the entrypoint.
     */
    private EntrypointHandler handleEntrypoint() {
        // create entrypoint handler
        EntrypointHandler entryHandler = new EntrypointHandler(("/broker/data-entrypoint.sh"));
        // process entrypoint
        entryHandler.processEntrypoint();
        // set entrypoint arguments
        List<String> args = container.getEntrypointArgs();
        entryHandler.setEntrypointArgs(args);

        return entryHandler;
    }

    /**
     * <p>
     * Handles the interaction with the new process.
     * <p>
     * The method creates a new process, initializes it with the environment,
     * starts the process, monitors its execution and updates the service status
     * to initialized, if it is initialized.
     *
     * @param entryHandler
     */
    private void handleProcess(EntrypointHandler entryHandler) {
        // create a process handler to manage the new process initiation.
        ProcessHandler procHandler = new ProcessHandler();
        // initialize with the environment
        Map<String, String> env = getConEnv();

        // add environment from dependencies
        String entrypointPath = entryHandler.getUpdatedEntrypointPath();
        List<String> entrypointArgs = entryHandler.getEntrypointArgs();
        procHandler.initProc(env, entrypointPath, entrypointArgs);
        // start process
        boolean procStarted = procHandler.startProc();
        // check if process has started successfully
        if (procStarted) {
            // change service status to INITIALIZED
            updateZkServiceStatus(serviceNode::setStatusInitialized);
            // monitor service and update status accordingly for zk service node
            monService(procHandler);
        } else {
            // change service status to NOT_INITIALIZED
            updateZkServiceStatus(serviceNode::setStatusNotInitialized);
        }

    }

    private Map<String, String> getDependenciesEnv() {
        for (Map.Entry<String, Container> entry : servicesConfiguration.entrySet()) {
            // get the container path 
            String path = entry.getKey();
            Container con = entry.getValue();
            if (con instanceof WebContainer) {
                // cast to instance class
                WebContainer web = (WebContainer) con;
                //web.ge WebEnvironment env = web.getEnvironment();
            } else if (con instanceof BusinessContainer) {
                // cast to instance class
                BusinessContainer bus = (BusinessContainer) con;
            } else if (con instanceof DataContainer) {
                // cast to instance class
                DataContainer data = (DataContainer) con;
            }
        }

        return null;
    }

    /**
     * Monitors the running service and updates the zk service node status 
     * accordingly in case it stops.
     *
     * @param procHandler the {@link ProcessHandler ProcessHandler} object.
     */
    private void monService(ProcessHandler procHandler) {
        // run in a new thread
        new Thread(() -> {
            procHandler.waitForMainProc();
            // change service status to NOT RUNNING
            updateZkServiceStatus(serviceNode::setStatusNotRunning);
        }
        ).start();
    }

    /**
     * Gets the container environments.
     *
     * @return a map with the container environment.
     */
    protected abstract Map<String, String> getConEnv();

    /**
     * Updates the service status to INITIALIZED or NOT, RUNNING, NOT RUNNING.
     */
    private void updateZkServiceStatus(Updatable updateInterface) {
        // get the service path
        String servicePath = resolveServiceName(containerName);
        // update status
        updateInterface.updateStatus();
        LOG.info("Updating service status to {}: {}", serviceNode.getStatus(), servicePath);
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
    protected final String resolveServiceName(String service) {
        return zkNamingService.concat("/").concat(service);
    }

    /**
     * Resolves a service/container path to the service/container name.
     *
     * @param path the service/container path to the zookeeper namespace.
     * @return the service name.
     */
    protected final String resolveServicePath(String path) {
        return path.substring(path.lastIndexOf("/") + 1, path.length());
    }

    /**
     * Returns the container type.
     *
     * @param path the path of the cotnainer zNode to find the container type.
     * @return the container type.
     */
    protected final String getContainerType(String path) {
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
     * De-serializes the container associated with the broker.
     * <p>
     * The method is inherited by subclasses in order to implement custom
     * functionality according to the container type (web, business, data).
     *
     * @param data the data to be de-serialized.
     * @return a container object with the configuration.
     */
    protected abstract Container deserializeConType(byte[] data);

    /**
     * De-serialiazes a container.
     *
     * @param path the path of the container.
     * @param data the data to deserialize.
     * @return a {@link Container Container} object.
     */
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
            LOG.error("Naming service node de-serialization FAILED! " + ex);
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
            LOG.warn("Interruption attemplted: {}", ex.getMessage());
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
                args[5] // conConfNode
        );

        // connect to zookeeper
        broker.connect();
        // create a new thread and start broker
        Thread thread = new Thread(broker, "BrokerThread-" + args[2]);
        thread.start();
    }
     */
}
