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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.freelabs.maestro.broker.process.*;
import net.freelabs.maestro.broker.services.ServiceManager;
import net.freelabs.maestro.broker.services.ServiceNode.SRV_CONF_STATUS;
import net.freelabs.maestro.broker.shutdown.Shutdown;
import net.freelabs.maestro.broker.shutdown.ShutdownNotifier;
import net.freelabs.maestro.broker.tasks.SubstEnv;
import net.freelabs.maestro.broker.tasks.Task;
import net.freelabs.maestro.broker.tasks.TaskExecutor;
import net.freelabs.maestro.core.generated.BusinessContainer;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.generated.Resources;
import net.freelabs.maestro.core.generated.Tasks;
import net.freelabs.maestro.core.generated.WebContainer;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.zookeeper.ZkConnectionWatcher;
import net.freelabs.maestro.core.zookeeper.ZkNamingService;
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
public abstract class Broker extends ZkConnectionWatcher implements Runnable, Shutdown {

    /**
     * The path of the Container to the zookeeper namespace.
     */
    private final String zkContainerPath;
    /**
     * Initial data for the zookeeper Broker node.
     */
    private static final String BROKER_ID = Long.toString(new Random().nextLong());
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Broker.class);
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
     * An object to handle execution of operations on another thread.
     */
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    /**
     * The name of the container associated with the broker.
     */
    private String containerName;
    /**
     * The work dir of the Broker, where created files will be stored.
     */
    private final String BROKER_WORK_DIR_PATH;
    /**
     * The container associated with the broker. Holds the configuration.
     */
    private Container container;
    /**
     * The znode of the container service to the naming service.
     */
    private final ZkNamingServiceNode conZkSrvNode;
    /**
     * Service Manager. Stores all the data for services-dependencies of the
     * container.
     */
    private ServiceManager srvMngr;
    /**
     * Handles the interaction with the naming service.
     */
    private final ZkNamingService ns;
    /**
     * Indicates weather the container is initialized.
     */
    private volatile boolean conInitialized;
    /**
     * Manages process execution.
     */
    private ProcessManager procMngr;
    /**
     *
     */
    public static final ShutdownNotifier SHUTDOWN = new ShutdownNotifier();

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
        this.shutdownNode = shutdownNode;
        this.conConfNode = conConfNode;
        containerName = resolveConPath(zkContainerPath);
        BROKER_WORK_DIR_PATH = BrokerConf.BROKER_BASE_DIR_PATH + File.separator + containerName + "-broker";
        // create a new naming service node
        conZkSrvNode = new ZkNamingServiceNode(zkContainerPath);
        // initialize the naming service object
        ns = new ZkNamingService(zkNamingService);
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
        waitForShutdown(SHUTDOWN);
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
            shutdown(SHUTDOWN);
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
                LOG.info("Waiting for container description: " + path);
                break;
            case OK:
                LOG.info("Container description found: " + path);
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
        zk.getData(conConfNode, false, getConDescriptionCallback, null);
    }

    /**
     * The object to call back with
     * {@link #getConDescription() getConDescription} method.
     */
    private final DataCallback getConDescriptionCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getConDescription();
                break;
            case NONODE:
                LOG.error("Node does NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("Getting container description: " + path);
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
        /* initialize the services manager to manage services-dependencies
        The dependencies are retrieved from the current cotnainer configuration,
        from "connectWith" field.        
         */
        List<String> srvNames = container.getConnectWith();
        Map<String, String> srvsNamePath = ns.getSrvsNamePath(srvNames);
        srvMngr = new ServiceManager(srvsNamePath);
        // set data to the container zNode 
        setZkConNodeData(data);
    }

    /**
     * Sets data to the container's zNode.
     */
    private void setZkConNodeData(byte[] data) {
        zk.setData(zkContainerPath, data, -1, setConZkNodeDataCallback, data);
    }

    /**
     * Callback to be used with
     * {@link #setZkConNodeData(byte[]) setZkConNodeData} method.
     */
    private final StatCallback setConZkNodeDataCallback = (int rc, String path, Object ctx, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                setZkConNodeData((byte[]) ctx);
                break;
            case NONODE:
                LOG.error("Cannot set data to znode. ZNODE DOES NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("Data set to container zNode: " + path);
                // register container service to naming service
                registerToServices();
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
        String path = ns.resolveSrvName(containerName);
        // set service status to NOT_INITIALIZED
        conZkSrvNode.setStatusNotInitialized();
        // serialize the node to byte array
        byte[] data = ns.serializeZkSrvNode(path, conZkSrvNode);
        // create the zNode of the service to the naming service
        createZkConSrvNode(path, data);
    }

    /**
     * Creates the service node for this container to the naming service.
     *
     * @param path the path of the zNode to the zookeeper namespace.
     * @param data the data of the zNode.
     */
    public void createZkConSrvNode(String path, byte[] data) {
        zk.create(path, data, OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL, createZkConSrvNodeCallback, data);
    }

    /**
     * The object to call back with {@link #createZkConSrvNode(java.lang.String, byte[])
     * createZkConSrvNode} method.
     */
    private final StringCallback createZkConSrvNodeCallback = (int rc, String path, Object ctx, String name) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                checkContainerNode(path, (byte[]) ctx);
                break;
            case NODEEXISTS:
                LOG.error("Service zNode already exists: " + path);
                break;
            case OK:
                LOG.info("Registered to naming service: " + path);
                /* query for service - get the configurarion of needed containers
                A service is offered by a container. 
                 */
                if (srvMngr.hasServices()) {
                    srvMngr.getServices().stream().forEach((service) -> {
                        queryForService(service);
                    });
                } else {
                    executorService.execute(() -> {
                        checkInitialization();
                    });
                }
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

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
    private void queryForService(String srvPath) {
        LOG.info("Querying for service: " + ns.resolveSrvPath(srvPath));
        // check if service has started
        serviceExists(srvPath);
    }

    /**
     * Checks if service exists.
     *
     * @param servicePath the path of the service under the naming service
     * namespace.
     */
    private void serviceExists(String servicePath) {
        zk.exists(servicePath, serviceWatcher, serviceExistsCallback, null);
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
                getZkSrvData(path);
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
    private final Watcher serviceWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeCreated) {
            LOG.info("Watched event: " + event.getType() + " for " + event.getPath() + " ACTIVATED.");
            // get data from service node
            getZkSrvData(event.getPath());
        } else if (event.getType() == NodeDataChanged) {
            LOG.info("Watched event: " + event.getType() + " for " + event.getPath() + " ACTIVATED.");
            //            
            // CODE TO MONITOR FOR UPDATES ON THE SERVICE NODE
            //
            //  get data from node
            getZkSrvUpdatedData(event.getPath());
        }
    };

    /**
     * Gets data from the requested service zNode.
     *
     * @param zkPath the path of the container to the zookeeper namespace.
     */
    private void getZkSrvData(String zkPath) {
        zk.getData(zkPath, serviceWatcher, getServiceDataCallback, null);
    }

    /**
     * The callback to be used with
     * {@link #getZkSrvData(java.lang.String) getZkSrvData(String)} method.
     */
    private final DataCallback getServiceDataCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getZkSrvData(path);
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
    private void processServiceData(byte[] data, String srvPath) {
        // de-serialize service node
        ZkNamingServiceNode node = ns.deserializeZkSrvNode(srvPath, data);
        // get the zNode path of the container of this service
        String zkConPath = node.getZkContainerPath();
        // store service info to the service manager 
        srvMngr.setSrvStateStatus(srvPath, node.getStatus());
        srvMngr.setSrvZkConPath(srvPath, zkConPath);
        // GET CONFIGURATION DATA FROM the container of the retrieved zkPath.
        getConData(zkConPath);
    }

    /**
     * Gets data from the container zNode.
     *
     * @param zkPath the path of the container zNode.
     */
    private void getConData(String zkPath) {
        zk.getData(zkPath, setConWatcher, getConDataDataCallback, null);
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
     * @param zkConpath the path of the container zNode.
     */
    private void processConData(byte[] data, String zkConPath) {
        LOG.info("Processing container data: {}", zkConPath);
        // deserialize container data 
        Container srvCon = deserializeDependency(zkConPath, data);
        // get cotnainer name
        String conName = resolveConPath(zkConPath);
        // save configuration to file named after the container
        createConfFile(srvCon, conName);
        // save container to service node
        srvMngr.setSrvNodeCon(zkConPath, srvCon);
        // set the service conf status of service-dependency to PROCESSED
        srvMngr.setSrvConfStatusProc(ns.resolveSrvName(conName));
        LOG.info("Service: {}\tStatus: {}.", conName, SRV_CONF_STATUS.PROCESSED.toString());
        // check if container is initialized in order to start the entrypoint process
        checkInitialization();
    }

    /**
     * *************************************************************************
     * PROCESS HANDLING
     * *************************************************************************
     */
    /**
     * <p>
     * Checks the necessary conditions for the container to be initialized and
     * for the processes to get started.
     * <p>
     * Checks if all container services-dependencies are processed and sets
     * {@link #conInitialized conInitialized} flag to true.
     * <p>
     * If {@link #conInitialized conInitialized} flag is true, checks if all
     * services-dependencies are initialized and bootstraps the process(es).
     */
    private synchronized void checkInitialization() {
        if (srvMngr.hasServices()) {
            // if container is not initialized
            if (!conInitialized) {
                // check if srvs are processed
                if (srvMngr.areSrvProcessed()) {
                    conInitialized = true;
                    LOG.info("Container INITIALIZED!");
                    // check if srvs are initialized
                    if (srvMngr.areSrvInitialized()) {
                        // start the entryoint process
                        bootstrapProcess();
                    }
                }
            } else // check if srvs are initialized
             if (srvMngr.areSrvInitialized()) {
                    // start processes
                    executorService.execute(() -> {
                        bootstrapProcess();
                    });
                }
        } else {
            conInitialized = true;
            LOG.info("Container INITIALIZED!");
            // execute in new thread
            executorService.execute(() -> {
                bootstrapProcess();
            });
        }
    }

    /**
     * Bootstraps container processes. The method:
     * <ul>
     * <li>Creates the {@link ProcessManager process manager} to manage process
     * execution.</li>
     * <li>Creates the {@link ResourceHandler resource handler}, that is
     * initialized with all the resources, to handle resource manipulation.</li>
     * <li>Creates the environment for the processes (env vars).</li>
     * <li>Creates and initializes the {@link ProcessHandler process handlers}
     * for the main and other processes, to handle process initialization and
     * initiation.</li>
     * <li>Starts the {@link ProcessManager process manager} that executes the
     * processes.</li>
     * </ul>
     */
    private void bootstrapProcess() {
        // create the process manager that will start processes
        procMngr = new ProcessManager();
        // create the resource handler to manipulate resources
        Resources res = container.getStart();
        ResourceHandler rh = new ResourceHandler(res.getPreMain(), res.getPostMain(), res.getMain());
        // create the environment shared with main and other processes
        Map<String, String> env = createEnvForMainProc();
        // create TaskExecutor to execute defined tasks
        TaskExecutor taskExec = initTaskExecutor(container.getTasks(), env);
        // get handler for the interaction with the main process
        MainProcessHandler mainProcHandler = initMainProc(rh, env);
        // get handlers for the interaction with processes scheduled before main
        List<ProcessHandler> preMainProcHandlers = initOtherProcs(rh, rh.getPreMainRes(), env);
        // get handlers for the interaction with processes scheduled after main
        List<ProcessHandler> postMainProcHandlers = initOtherProcs(rh, rh.getPostMainRes(), env);
        // set configuration to process manager
        procMngr.setMainProcHandler(mainProcHandler);
        procMngr.setPreMainProcHandlers(preMainProcHandlers);
        procMngr.setPostMainProcHandlers(postMainProcHandlers);

        // execute tasks
        taskExec.execTasks();

        // start processes
        procMngr.startProcesses();
    }

    /**
     * <p>
     * Creates and initializes an executor for tasks.
     * <p>
     * A task is a function of some type for the application.
     *
     * @param tasks the Tasks object defined in schema.
     * @param env the environment of the processes.
     * @return an object that will handle task execution.
     */
    private TaskExecutor initTaskExecutor(Tasks tasks, Map<String, String> env) {
        TaskExecutor taskExec;
        // init substEnv task
        if (tasks != null) {
            List<String> substEnvPaths = tasks.getSubstEnv();
            Task substEnv = new SubstEnv(substEnvPaths, env);
            taskExec = new TaskExecutor(substEnv);
        }else{
            taskExec = new TaskExecutor();
        }
        return taskExec;
    }

    /**
     * <p>
     * Sets all initialization configuration for the main process.
     * <p>
     * Creates and initializes the {@link MainProcessData MainProcessData}
     * object that stores all data related to the main process.
     * <p>
     * Creates and initializes the {@link MainProcessHandler MainProcessHandler}
     * object that handles the interaction with the main process.
     *
     * @param rh the resource handler to query for resources to run.
     * @param env the environment of the process.
     * @return the {@link MainProcessHandler MainProcessHandler} object that
     * handles the interaction with the main process. NUll if there was a
     * problem with the main resource.
     */
    private MainProcessHandler initMainProc(ResourceHandler rh, Map<String, String> env) {
        MainProcessHandler pHandler = null;
        // check if main resource is ok
        if (rh.isResourceOk(rh.getMainRes())) {
            // get the port the proc is listening
            int procPort = getHostPort();
            // create and init the object that stores all the process configuration
            MainProcessData pdata = new MainProcessData(rh.getMainRes(), env, "localhost", procPort);
            // create and handler for main process execution
            pHandler = new MainProcessHandler(pdata);
            // set code to execute if process executed successfully
            pHandler.setExecOnSuccess(() -> {
                // change service status to INITIALIZED
                updateZkSrvStatus(conZkSrvNode::setStatusInitialized);
                // monitor service and update status accordingly for zk service node
                monService();
            });
            // set code to execute if process failed to execute
            boolean running = pHandler.isMainProcRunning();
            pHandler.setExecOnFailure(() -> {
                if (running) {
                    // change service status to NOT_INITIALIZED
                    updateZkSrvStatus(conZkSrvNode::setStatusNotInitialized);
                } else {
                    // change service status to NOT_INITIALIZED
                    updateZkSrvStatus(conZkSrvNode::setStatusNotRunning);
                }
            });
        }
        return pHandler;
    }

    /**
     * <p>
     * If there are other processes to execute except main process, this method
     * initializes the {@link ProcessHandler process handlers} that will execute
     * the processes.
     * <p>
     * At first, a resource is checked for errors. Then the process handler is
     * initialized with the resource and the environment.
     *
     * @param rh the resource handler, used to manipulate resources.
     * @param resources the list of the resources to execute.
     * @param env the environment of the processes.
     * @return the list of process handlers initialized to start processes.
     */
    private List<ProcessHandler> initOtherProcs(ResourceHandler rh, List<Resource> resources, Map<String, String> env) {
        List<ProcessHandler> pHandlers = new ArrayList<>();
        // iterate through resources
        for (Resource res : resources) {
            // check resource for errors
            if (rh.isResourceOk(res)) {
                // create and init the obj that stores all necessary process data
                ProcessData pData = new ProcessData(res, env);
                // create and init the obj that handles the process execution
                DefaultProcessHandler ph = new DefaultProcessHandler(pData);
                // add handler to list
                pHandlers.add(ph);
            }
        }
        return pHandlers;
    }

    /**
     * <p>
     * Creates the environment for the main process that will be shared with
     * other processes, if any.
     * <p>
     * The environment consists of all the environment variables that are
     * available to the container to run processes.
     *
     * @return a map with all the environment variables needed for the container
     * processes.
     */
    private Map<String, String> createEnvForMainProc() {
        // get environment from the container
        Map<String, String> env = getConEnv();
        // get environment from dependencies and add to environment
        env.putAll(getDependenciesEnv());
        return env;
    }

    /**
     * Extracts the environment from the services-dependencies of the main
     * container service.
     *
     * @return a map with all the environment variables defines in services-
     * dependencies.
     */
    private Map<String, String> getDependenciesEnv() {
        LOG.info("Extracting environment from dependencies.");
        // holds the global key-value entries for all container
        Map<String, String> dependenciesEnv = new HashMap<>();
        // holds data per repetition
        Map<String, String> env = new HashMap<>();

        // iterate through the container dependencies
        for (Container con : srvMngr.getConsOfSrvs()) {
            if (con instanceof WebContainer) {
                // cast to instance class
                WebContainer web = (WebContainer) con;
                // get the environment
                env = web.getEnvironment().getEnvMap(web.getEnvironment(), con.getName());
            } else if (con instanceof BusinessContainer) {
                // cast to instance class
                BusinessContainer business = (BusinessContainer) con;
                // get the environment
                env = business.getEnvironment().getEnvMap(business.getEnvironment(), con.getName());
            } else if (con instanceof DataContainer) {
                // cast to instance class
                DataContainer data = (DataContainer) con;
                // get the environment
                env = data.getEnvironment().getEnvMap(data.getEnvironment(), con.getName());
            }
            // print extracted environment
            LOG.info("Environment of dependency: {}", con.getName());
            for (Map.Entry<String, String> entry : env.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                LOG.info("{}={}", key, value);
            }
            // add map to dependencies map
            dependenciesEnv.putAll(env);
            // remove everything
            env.clear();
        }
        return dependenciesEnv;
    }

    /**
     * Monitors the running service and updates the zk service node status
     * accordingly in case it stops.
     *
     * @param procHandler the {@link MainProcessHandler MainProcessHandler}
     * object.
     */
    private void monService() {
        // run in a new thread
        new Thread(() -> {
            procMngr.waitForMainProc();
            if (!Thread.interrupted()) {
                // change service status to NOT RUNNING
                updateZkSrvStatus(conZkSrvNode::setStatusNotRunning);
            }
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
     *
     * @return the port at which the main process runs.
     */
    protected abstract int getHostPort();

    /**
     * Updates the service state status of a {@link ZkNamingServiceNode
     * ZkNamingServiceNode}.
     *
     * @param updateInterface the update action.
     */
    private void updateZkSrvStatus(Updatable updatableObj) {
        // get the service path
        String servicePath = ns.resolveSrvName(containerName);
        // update status
        updatableObj.updateStatus();
        LOG.info("Updating service status to {}: {}", conZkSrvNode.getStatus(), servicePath);
        // serialize data
        byte[] updatedData = ns.serializeZkSrvNode(servicePath, conZkSrvNode);
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
     * Watcher to be used in
     * {@link  #setConWatch(java.lang.String) setConWatch(String)} method.
     */
    private final Watcher setConWatcher = (WatchedEvent event) -> {
        LOG.info(event.getType() + ", " + event.getPath());

        if (event.getType() == NodeDataChanged) {
            LOG.info("Watched event: " + event.getType() + " for " + event.getPath() + " ACTIVATED.");
            /**
             *
             * CODE FOR RETRIEVING UPDATES ON CONTAINER STATE
             *
             *
             */

            // RE-SET WATCH TO KEEP MONITORING THE CONTAINER
        }
    };

    /**
     * Gets data from the requested service zNode.
     *
     * @param zkPath the path of the container to the zookeeper namespace.
     */
    private void getZkSrvUpdatedData(String zkPath) {
        zk.getData(zkPath, serviceWatcher, getZkSrvUpdatedDataDataCallback, null);
    }

    /**
     * The callback to be used with
     * {@link #getZkSrvUpdatedData(java.lang.String) getZkSrvUpdatedData(String)}
     * method.
     */
    private final DataCallback getZkSrvUpdatedDataDataCallback = (int rc, String path, Object ctx, byte[] data, Stat stat) -> {
        switch (KeeperException.Code.get(rc)) {
            case CONNECTIONLOSS:
                LOG.warn("Connection loss was detected");
                getZkSrvUpdatedData(path);
                break;
            case NONODE:
                LOG.error("CANNOT GET DATA from SERVICE. Service node DOES NOT EXIST: " + path);
                break;
            case OK:
                LOG.info("Getting data from service: " + path);
                // process retrieved data from requested service zNode
                executorService.execute(() -> {
                    processZkSrvUpdatedData(path, data);
                });
                break;
            default:
                LOG.error("Something went wrong: ",
                        KeeperException.create(KeeperException.Code.get(rc), path));
        }
    };

    private void processZkSrvUpdatedData(String path, byte[] data) {
        // de-serialize service node
        ZkNamingServiceNode srvNode = ns.deserializeZkSrvNode(path, data);
        // set the new service status
        srvMngr.setSrvStateStatus(path, srvNode.getStatus());
        // log
        LOG.info("Status of service {} is: {}", path, srvNode.getStatus().toString());
        // check if all services are initialized
        checkInitialization();
    }

    /**
     * Resolves a container path to the container name.
     *
     * @param path the zxNode container path to the zookeeper namespace.
     * @return the container name.
     */
    public final String resolveConPath(String path) {
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
                LOG.info("De-serialized dependency: {}. Printing: \n {}", resolveConPath(path),
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
     * Sets a latch to wait for shutdown.
     */
    @Override
    public void waitForShutdown(ShutdownNotifier notifier) {
        try {
            notifier.waitForShutDown();
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
    @Override
    public void shutdown(ShutdownNotifier notifier) {
        // shut down then executorService to free resources
        executorService.shutdownNow();
        // close zk client session
        stop();
        // log event
        LOG.info("Initiating Broker shutdown " + zkContainerPath);
        notifier.shutDown();
    }

}
