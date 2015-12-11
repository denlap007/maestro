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
import java.lang.ProcessBuilder.Redirect;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.zookeeper.ConnectionWatcher;
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
     * The user configuration for the container.
     */
    private Map<String, Object> containerConf;

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
     * A Map that holds all the configuration maps of the needed containers.
     */
    private final Map<String, Map<String, Object>> servicesConfiguration = new HashMap<>();
    
    private Container container;

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
        containerType = zkContainerPath.substring(zkContainerPath.indexOf("/") + 1, zkContainerPath.lastIndexOf("/") - 1);
        BROKER_WORK_DIR_PATH = BrokerConf.BROKER_BASE_DIR_PATH + File.separator + containerName + "-broker";
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
                // check if this node is crated by this client
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
                    createConfFile(getContainerConf(), containerName);
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
        setContainerConf(deserializeConf(data));
        // set data to the container zNode 
        setNodeData(data);
        // register container as service to the naming service
        registerToServices();
        /* query for service - get the configurarion of needed containers
        A service is offered by a container. The needed services are retrieved
        from the current cotnainer configuration from "connectWith" field.
         */
        for(String service : container.getConnectWith()){//for (String service : (List<String>) containerConf.get("connectWith")) {
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
        // get the zk path of the container and encode it to byte array
        byte[] data = JsonSerializer.encodeUTF8(zkContainerPath);
        // get the service path to the naming service
        String path = resolveServiceName(containerName);
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
     * Data stored to the service zNode, resolve service name to the zkPath of
     * the container offering that service.
     * <p>
     * Data retrieved is exptected to be a plain String.
     *
     * @param data
     * @param path
     */
    private void processServiceData(byte[] data, String path) {
        // decode byte array into String
        String containerPath = JsonSerializer.decodeUTF8(data);
        // print data
        LOG.info("Retrieved from: {}. Data: {} ", path, containerPath);
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
        Map<String, Object> conf = deserializeConf(data);
        // get cotnainer name
        String conName = path.substring(path.lastIndexOf("/") + 1, path.length());
        // save configuration to file named after the container
        createConfFile(conf, conName);
        // save service configuration to memory
        saveServiceConf(conf, conName);
        // set service status and log the event
        serviceState.replace(conName, SERVICE_STATE.PROCESSED);
        LOG.info("Service: {}\tStatus: {}.", conName, SERVICE_STATE.PROCESSED.toString());
        // check if the container is initialized in order to start the main process
        if (isContainerInitialized() == true) {
            LOG.info("Container initialization COMPLETE!");
            startMainProcess();
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
     * Saves the configuration of a service to memory.
     *
     * @param conf the configuration of the service.
     * @param serviceName the name of the service.
     */
    private void saveServiceConf(Map<String, Object> conf, String serviceName) {
        servicesConfiguration.put(serviceName, conf);
    }

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
    private void createConfFile(Map<String, Object> data, String fileName) {
        // create the final file path
        String path = BROKER_WORK_DIR_PATH + File.separator + fileName;
        // create new file
        File newFile = new File(path);
        // save data to file
        try {
            JsonSerializer.saveToFile(newFile, data);
            // log event
            LOG.info("Created configuration file: {}", path);
        } catch (IOException ex) {
            LOG.error("FAILED to create configuration file: " + ex);
        }
    }

    /**
     * Starts the main process of the associated container.
     */
    private void startMainProcess() {
        // create a new process builder to initialize the process
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "echo $web_IP;");
        // get the environment 
        Map<String, String> env = pb.environment();
        // initialize the environment
        for (Map.Entry<String, Map<String, Object>> entry : servicesConfiguration.entrySet()) {
            String serviceName = entry.getKey();
            Map<String, Object> serviceConf = (Map<String, Object>) entry.getValue();
            LOG.info("Configuration map size: " + serviceConf.size());
            for (Map.Entry<String, Object> entry2 : serviceConf.entrySet()) {
                String key = entry2.getKey();
                LOG.info("This is key: " + key);
                if (key.equals("IP")){
                    key = serviceName + key;
                    LOG.info("This is new key: " + key);
                    String value = (String) entry2.getValue();
                    LOG.info("This is value: " + value);
                    env.put(key, value);
                }
               /*     
                String value = (String) entry2.getValue();
                if (value instanceof String) {
                    // add the service name as prefix, so that the keys from 
                    // different services are unique.
                    key = serviceName + key;
                    // export as environment variable
                    env.put(key, (String) value);
                }
                */
            }
        }

        /*System.out.println("Working directory for process: " + pb.directory());
        pb.directory(new File("myDir"));
        File log = new File("log");
        pb.redirectErrorStream(true);
        pb.redirectOutput(Redirect.appendTo(log));*/
        pb.redirectOutput(Redirect.INHERIT);
        try {
            Process p = pb.start();
            LOG.info("Main process STARTED.");
        } catch (IOException ex) {
            LOG.error("FAILED to start main process: {}", ex);
        }
    }

    private void findContainerType(String containerPath) {
        if (containerPath.contains("WebContainer")) {

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
     * De-serializes container configuration.
     *
     * @param data the data to be deserialized.
     * @return the deserialized data as a Map<String, Object>.
     */
    private Map<String, Object> deserializeConf(byte[] data) {
        Map<String, Object> conf = null;
        try {
            conf = JsonSerializer.deserializeToMap(data);
            LOG.info("Configuration deserialized! Printing: \n {}", JsonSerializer.deserializeToString(data));
        } catch (IOException ex) {
            LOG.error("De-serialization FAILED: " + ex);
        }
        return conf;
    }
    
    private void deserializeConfToContainer(byte[] data){
        try {
            container =  JsonSerializer.deserializeToConatiner(data);
            LOG.info("Configuration deserialized! Printing: \n {}", JsonSerializer.deserializeToString(data));
        } catch (IOException ex) {
            LOG.error("De-serialization FAILED: " + ex);
        }
    }

    /**
     * Serializes container configuration.
     *
     * @param conf the container configuration.
     * @return a byte array with the serialzed configuration.
     */
    private byte[] serializeConf(Map<String, Object> conf) {
        byte[] data = null;
        try {
            data = JsonSerializer.serialize(conf);
            LOG.info("Configuration serialized SUCCESSFULLY!");
        } catch (JsonProcessingException ex) {
            LOG.error("Serialization FAILED: " + ex);
        }
        return data;
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

    /**
     * @return the containerConf
     */
    private synchronized Map<String, Object> getContainerConf() {
        return containerConf;
    }

    /**
     * @param containerConf the containerConf to set
     */
    private synchronized void setContainerConf(Map<String, Object> containerConf) {
        this.containerConf = containerConf;
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
