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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
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
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that defines a Broker client to the zookeeper configuration store. Must
 * implement the BrokerInterface.
 */
public class Broker extends ConnectionWatcher implements BrokerInterface, Runnable {

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
    private String containerConf;
    /**
     * The default charset to be used with encoding/decoding string to/from byte
     * array.
     */
    private static final Charset CHARSET = Charset.forName("UTF-8");

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
    }

    /**
     * Method implementation of run method from Runnable interface.
     */
    @Override
    public void run() {
        runBroker();
    }

    @Override
    public void runBroker() {
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
                startProcessing(data);
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
    private void startProcessing(byte[] data) {
        // deserialize configuration FOR DEBUG
        containerConf = deserializeConf(data);
        // set data to container zNode 
        setNodeData(data);
        //register to naming service
        registerToServices();
        // query for service - get the configurarion of needed containers
        queryForService("web");
        // get container name
        String containerName = getConfValue("name");
        //create conf file for container associated with the broker
        createConfFile(containerConf, "/".concat(containerName));
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
        // get the name of the container
        String containerName = getConfValue("name");
        // create the path to the naming service
        String path = zkNamingService + "/" + containerName;
        // get the zk path of the container
        byte[] data = zkContainerPath.getBytes(Charset.forName("UTF-8"));
        // create the zNode to tha naming service
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
        String servicePath = zkNamingService + "/" + name;
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
    public void getServiceData(String zkPath) {
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
        String containerPath = decodeUTF8(data);
        // print data
        LOG.info("Retrieved data from: {}. Data: {} ", path, containerPath);
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
                processContainerData(data, path);
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
        // deserialize container data to string
        String containerData = deserializeConf(data);
        // save configuration to file
        createConfFile(containerData, path);
    }

    /**
     * <p>
     * Creates a file with the container configuration.
     * <p>
     * Data to be written must be in json format.
     * <p>
     * The file is created to the BROKER_WORK_DIR directory. The full path of
     * the file is derived from the BROKER_WORK_DIR followed by the zkPath of
     * the container.
     */
    private void createConfFile(String data, String path) {
        // create final path
        String containerName = getConfValue("name");
        path = BrokerConf.BROKER_WORK_DIR_PATH + File.separator + containerName + "-broker" + path;
        ObjectMapper mapper = new ObjectMapper();
        // set to write json indented
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // craete a writer 
        ObjectWriter writer = mapper.writer();

        try {
            // read into a new Object
            Object json = mapper.readValue(data, Object.class);
            // create new file
            File newFile = new File(path);
            // make dirs if necessary
            newFile.getParentFile().mkdirs();
            // write Object to file
            writer.writeValue(newFile, json);
            // log event
            LOG.info("Created configuration file: {}", path);
        } catch (IOException ex) {
            LOG.error("Something went wrong: " + ex);
        }
    }

    /**
     * Starts the main service of the started container.
     */
    private void startContainerService() {

    }

    private void getConfFromContainers() throws IOException {
        List<String> containersList = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(containerConf);
        JsonNode connectWithNode = rootNode.path("connectWith");
        Iterator<JsonNode> iterator = connectWithNode.elements();

        while (iterator.hasNext()) {
            JsonNode container = iterator.next();
            LOG.info("Needed configuration from: " + container.asText());
            containersList.add(container.asText());
        }
    }

    private void exportEnvVars() {
        try {
            // read configuration file
            FileReader reader = new FileReader(BrokerConf.BROKER_SERVICE_SCRIPT_PATH);
        } catch (FileNotFoundException ex) {
            java.util.logging.Logger.getLogger(Broker.class.getName()).log(Level.SEVERE, null, ex);
        }

        // JSONParser jsonParser = new JSONParser();
    }

    /**
     * Deserializes configuration.
     *
     * @param data the data to be deserialized.
     * @return the deserialized data as String.
     */
    private String deserializeConf(byte[] data) {
        String str = JsonSerializer.deserialize(data);
        LOG.info("Configuration deserialized! Printing: \n {}", str);
        return str;
    }

    /**
     * Encodes a String in byte array using UTF-8.
     *
     * @param str the String to encode.
     * @return the encoded byte array.
     */
    private byte[] encodeUTF8(String str) {
        return str.getBytes(CHARSET);
    }

    /**
     * Decodes a byte array to String using UTF-8.
     *
     * @param data the byte array to be decoded.
     * @return a string representing the decoded byte array.
     */
    private String decodeUTF8(byte[] data) {
        return (new String(data, CHARSET));
    }

    /**
     * Returns a value that corresponds to the key contained in the container
     * configuration.
     *
     * @param key the key which binded value is retrieved.
     * @return the value of the specified key.
     */
    private String getConfValue(String key) {
        ObjectMapper mapper = new ObjectMapper();
        String value = null;

        try {
            JsonNode rootNode = mapper.readTree(containerConf);
            JsonNode elem = rootNode.path(key);
            value = elem.asText();
            LOG.info("Retrieved KV pair: " + key + ":" + value);
        } catch (IOException ex) {
            LOG.error("Something went wrong: " + ex);
        }

        return value;
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
        LOG.info("Initiating Broker shutdown " + zkContainerPath);
    }

    // ------------------------------- MAIN ------------------------------------
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

}
