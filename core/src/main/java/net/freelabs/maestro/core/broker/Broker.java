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
package net.freelabs.maestro.core.broker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.core.command.PullImageResultCallback;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.JAXBException;
import net.freelabs.maestro.core.generated.BindMnt;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.Docker;
import net.freelabs.maestro.core.generated.ExposePort;
import net.freelabs.maestro.core.generated.Protocol;
import net.freelabs.maestro.core.generated.PublishPort;
import net.freelabs.maestro.core.handler.NetworkHandler;
import net.freelabs.maestro.core.serializer.JAXBSerializer;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkMaster;
import net.freelabs.maestro.core.zookeeper.ZkNode;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * <p>
 * Abstract class that provides methods to initialize and boot a container.
 * <p>
 * For every container type, there is a subclass of this class that handles
 * initialization and bootstrapping of the container.
 */
public abstract class Broker implements ContainerLifecycle {

    /**
     * The container associated with the broker.
     */
    protected final Container con;
    /**
     * The zookeeper configuration.
     */
    protected final ZkConf zkConf;
    /**
     * The docker client that will communicate with the docker daemon.
     */
    protected final DockerClient docker;
    /**
     * A Logger object.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(Broker.class);
    /**
     * A ZkNode object that holds all the zk configuration about this container.
     */
    protected final ZkNode zNode;
    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads.
     */
    private final CountDownLatch shutdownSignal;
    /**
     * Number of times to try and execute code passed to {@link
     * #runAndRetry(net.freelabs.maestro.core.broker.CoreBroker.RunCmd, int) runAndRetry}.
     */
    private static final int RETRY_ATTEMPTS = 3;
    /**
     * Max attempts to pull an image from docker hub.
     */
    private static final int PULL_ATTEMPTS = 3;
    /**
     * The arguments used with the boot command to boot the container.
     */
    private String conBootArgs;
    /**
     * The command that boots the container.
     */
    private String conBootCmd;
    /**
     * The environment with which the container is initialized at boot. This
     * holds all the environment variables passed for proper boot and init.
     */
    private String conBootEnv;
    /**
     * Handles requests and interaction with zookeeper.
     */
    private final ZkMaster zkMaster;
    /**
     * Flag for errors from zookeeper operations.
     */
    private boolean zkError;
    /**
     * Data upload with download paths in case of data transfer.
     */
    private final Map<String, String> upDownPaths;
    /**
     * Holds information about application networks.
     */
    private final NetworkHandler netHandler;
    /**
     * The path of the .jar file to execute the Broker in the container.
     */
    private static final String BROKER_JAR_IN_CONTAINER = "/opt/maestro/bin/broker.jar";
    /**
     * Time that services are waited to stop.
     */
    private static final long SERVICES_TIMEOUT = 2;

    private static final TimeUnit SERVICES_TIMEOUT_UNIT = TimeUnit.MINUTES;

    /**
     * Handles errors.
     */
    //private final ErrorHandler errHandler; 
    /**
     * Constructor
     *
     * @param zkConf the zookeeper configuration.
     * @param con the container which will be bound to the broker.
     * @param dockerClient a docker client to communicate with the docker
     * daemon.
     * @param zkMaster handles interaction with zookeeper service.
     * @param netHandler handles interaction with application networks.
     */
    public Broker(ZkConf zkConf, Container con, DockerClient dockerClient, ZkMaster zkMaster, NetworkHandler netHandler) {
        this.zkConf = zkConf;
        this.con = con;
        this.docker = dockerClient;
        this.zkMaster = zkMaster;
        this.netHandler = netHandler;
        shutdownSignal = new CountDownLatch(1);
        upDownPaths = new HashMap<>();
        if (con != null) {
            zNode = zkConf.getContainers().get(con.getName());
        } else {
            zNode = null;
        }
    }

    /**
     * Runs the start state for the Broker. In the start state, the Broker
     * creates the container configuration, starts the container and runs the
     * postStart state where it updates the znode data for the container and
     * finally creates the configuration node to zookeeper.
     *
     * @return true if there were no errors during execution.
     */
    public boolean onStart() {
        boolean success = false;
        // create configration to initialize parameters
        createContainerEnv();
        // create container instance
        CreateContainerResponse container = createContainer();
        // check if container was created 
        if (container != null) {
            // start the created container instance
            String cid = startContainer(container, con.getName());
            // check for errors
            if (cid != null) {
                // copy data, if any, to container
                boolean copied = copyToContainer(cid);
                if (copied) {
                    // get container IP
                    success = onPostStart(cid);
                }
            } else {
                LOG.error("FAILED to start container.");
            }
        }
        if (!success) {
            shutdown();
        }
        return success;
    }

    /**
     * Copies data from local host paths to container paths.
     *
     * @param cid the container id.
     * @return true if operation completed successfully.
     */
    private boolean copyToContainer(String cid) {
        LOG.info("Copying files from host to container for service {}...", con.getName());
        boolean success = true;

        for (Map.Entry<String, String> entry : upDownPaths.entrySet()) {
            String hostPath = entry.getKey();
            String containerPath = entry.getValue();

            try {
                docker.copyArchiveToContainerCmd(cid)
                        .withDirChildrenOnly(true)
                        .withRemotePath(containerPath)
                        .withHostResource(hostPath)
                        .exec();
            } catch (Exception ex) {
                LOG.error("Something went wrong: {}", ex);
                success = false;
                break;
            }
        }
        return success;
    }

    /**
     * Attaches the container to a network.
     *
     * @param cid the container id.
     * @param netId the network id.
     * @return true if the container connected to application network
     * successfully.
     */
    private boolean attachToNetwork(String cid, String netId) {
        LOG.info("Attaching container for service {} to network...", con.getName());
        boolean success = false;
        if (netId != null) {
            try {
                docker.connectToNetworkCmd()
                        .withContainerId(cid)
                        .withNetworkId(netId)
                        .exec();
                success = true;
            } catch (Exception ex) {
                LOG.error("Something went wrong: {}", ex);
            }
        }
        return success;
    }

    /**
     * Runs the postStart state for the Broker. In the postStart state, the
     * Broker takes any action necessary after the container has started.
     *
     * @param cid the container identifier, id or name.
     * @return true if operations completed without errors.
     */
    public boolean onPostStart(String cid) {
        boolean success = false;
        String IP = getContainerIP(cid);
        // update container ip
        updateIP(IP);

        try {
            LOG.info("Updating zookeeper configuration for service {}...", zNode.getName());
            // update zNode configuration
            zNode.setData(JAXBSerializer.serialize(con));
            // log the event

            LOG.debug(JAXBSerializer.deserializeToString(zNode.getData()));
            // create zk configuration node
            createNode(zNode.getConfNodePath(), zNode.getData());
            // Sets the thread to wait until it's time to shutdown
            waitForShutdown();
            success = !zkError;
        } catch (JAXBException ex) {
            LOG.error("FAILED to update container IP. {}", ex);
        }
        return success;
    }

    public boolean onRestart() {
        boolean success = false;
        // restart the container with the deployed name
        String deplName = zkConf.getDeplCons().get(con.getName());
        boolean restarted = restartContainer(deplName, con.getName());

        if (restarted) {
            // run post start state
            success = onPostStart(deplName);
        }
        return success;
    }

    public boolean onStop() {
        boolean success = false;
        // register watch to services
        List<String> services = zkMaster.watchServices();
        // if no error
        if (services != null) {
            // if no services
            if (!services.isEmpty()) {
                // create shutdown node
                zkMaster.signalAppShutdown();
                // if shutdown node was created without errors
                if (!zkMaster.isMasterError()) {
                    // wait services to stop
                    boolean  stoppedSrvsWithoutError = zkMaster.waitServicesToStop(services, SERVICES_TIMEOUT, SERVICES_TIMEOUT_UNIT);
                    // check for running containers
                    Map<String, String> runningCons = getRunningCons(zkConf.getDeplCons());
                    // if containers still running force stop
                    boolean stoppedContainersWithoutError = true;
                    if (!runningCons.isEmpty()) {
                        stoppedContainersWithoutError = stopRunningCons(runningCons);
                    }
                    // check that running cons stopped successfully
                    if (stoppedContainersWithoutError) {
                        LOG.info("All Containers stopped.");
                    }
                    success = stoppedContainersWithoutError && stoppedSrvsWithoutError;
                }
            } else {
                LOG.info("All Services stopped.");
                // check for running containers even though services are not running
                Map<String, String> runningCons = getRunningCons(zkConf.getDeplCons());
                // if there are containers still running force stop
                if (!runningCons.isEmpty()) {
                    success = stopRunningCons(runningCons);
                    // check that running cons stopped successfully
                    if (success) {
                        LOG.info("All Containers stopped.");
                    }
                } else {
                    success = true;
                    LOG.warn("No Containers-Services running.");
                }
            }
        }

        return success;
    }

    /**
     * Gets a map with the defined-deployed container names of the containers
     * that are running.
     *
     * @param deplCons map with the defined-deployed container names of the
     * deployed containers.
     * @return map of the defined-deployed container names of the containers at
     * running state.
     */
    private Map<String, String> getRunningCons(Map<String, String> deplCons) {
        // map with found running containers if any
        Map<String, String> runningCons = new HashMap<>();
        // iterate and check running state
        LOG.info("Querying state of containers...");
        for (Map.Entry<String, String> entry : deplCons.entrySet()) {
            String defName = entry.getKey();
            String deplname = entry.getValue();
            try {
                InspectContainerResponse inspResp = docker.inspectContainerCmd(deplname).exec();
                // if container running add to map
                if (inspResp.getState().getRunning()) {
                    runningCons.put(defName, deplname);
                } else {
                    LOG.info("Container for service {} has stopped.", defName);
                }
            } catch (NotFoundException ex) {
                LOG.error("Container for service {} does not exist.", defName);
            }
        }
        return runningCons;
    }

    /**
     * Stops the containers that are still in running state.
     *
     * @param runningCons map of the defined-deployed container names of the
     * containers that are running.
     */
    private boolean stopRunningCons(Map<String, String> runningCons) {
        boolean success = true;
        boolean threwExeception = false;
        for (Map.Entry<String, String> entry : runningCons.entrySet()) {
            String defName = entry.getKey();
            String deplname = entry.getValue();
            try {
                LOG.warn("Container for service {} is still running. Forcing stop...", defName);
                success = stopContainer(deplname, defName);
            } catch (NotFoundException ex) {
                LOG.error("Container for service {} does not exist.", defName);
                success = false;
                threwExeception = true;
            }
        }
        return success && !threwExeception;
    }

    /**
     * Updates the IP of the container.
     *
     * @param IP the container IP.
     */
    private void updateIP(String IP) {
        con.getEnv().setHost_IP(IP);
    }

    /**
     * Gets the IP of the container with this container ID.
     *
     * @param containerId
     * @return
     */
    private String getContainerIP(String containerId) {
        // inspect container with id
        InspectContainerResponse response = docker.inspectContainerCmd(containerId).exec();
        // get network settings 
        NetworkSettings settings = response.getNetworkSettings();
        // get Networks
        Map<String, NetworkSettings.Network> netMap = settings.getNetworks();
        // return the cotnainer's IP  FROM THE DEFAULT APP NETWORK
        String netName = zkConf.getAppDefaultNetName();
        return netMap.get(netName).getIpAddress();
    }

    /**
     * Creates a zNode.
     *
     * @param path the path of the zNode.
     * @param data the data of the zNode.
     */
    private void createNode(String path, byte[] data) {
        zkMaster.createNodeAsync(path, data, OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, createNodeCallback, data);
    }

    /**
     * Callback object to be used with
     * {@link #createNode(String, byte[]) createNode} method.
     */
    private final AsyncCallback.StringCallback createNodeCallback = new AsyncCallback.StringCallback() {
        @Override
        public void processResult(int rc, String path, Object ctx, String name) {
            switch (KeeperException.Code.get(rc)) {
                case CONNECTIONLOSS:
                    LOG.warn("Connection loss was detected. Retrying...");
                    checkNode(path, (byte[]) ctx);
                    break;
                case NODEEXISTS:
                    LOG.error("Node already exists: " + path);
                    zkError = true;
                    shutdown();
                    break;
                case OK:
                    LOG.debug("Created zNode: " + path);
                    shutdown();
                    break;
                default:
                    zkError = true;
                    LOG.error("Something went wrong: ",
                            KeeperException.create(KeeperException.Code.get(rc), path));
                    shutdown();
            }
        }
    };

    /**
     * Checks if a zNode is created.
     *
     * @param path the path of the zNode.
     * @param data the data of the zNode.
     */
    private void checkNode(String path, byte[] data) {
        zkMaster.getDataAsync(path, false, checkNodeCallback, data);
    }

    /**
     * Callback object to be used with
     * {@link #checkNode(String, byte[]) checkNode} method.
     */
    private final AsyncCallback.DataCallback checkNodeCallback = new AsyncCallback.DataCallback() {
        @Override
        public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
            switch (KeeperException.Code.get(rc)) {
                case CONNECTIONLOSS:
                    LOG.warn("Connection loss was detected. Retrying...");
                    checkNode(path, (byte[]) ctx);
                    break;
                case NONODE:
                    createNode(path, (byte[]) ctx);
                    break;
                case OK:
                    /* check if this node was created by this process. In order to
                    do so, compare the zNode's stored data with the initialization data
                    for that node.
                     */
                    if (Arrays.equals(data, (byte[]) ctx) == true) {
                        LOG.info("ZkNode created successfully: " + path);
                        shutdown();
                    } else {
                        LOG.error("ΖkNode exists but was NOT created by this client: " + path);
                        zkError = true;
                        shutdown();
                    }
                    break;
                default:
                    zkError = true;
                    LOG.error("Something went wrong: ",
                            KeeperException.create(KeeperException.Code.get(rc), path));
                    shutdown();
            }
        }
    };

    public void waitForShutdown() {
        try {
            shutdownSignal.await();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Thread Interrupted. Stopping");
            // set the interrupt status
            Thread.currentThread().interrupt();
            LOG.info("Initiating Broker shutdown.");
        }
    }

    public void shutdown() {
        LOG.debug("Initiating Broker shutdown.");
        // release latch to finish execution
        shutdownSignal.countDown();
    }

    @Override
    public void createContainerEnv() {
        // set boot environment configuration
        String ZK_HOSTS = zkConf.getZkSrvConf().getHosts();
        String ZK_SESSION_TIMEOUT = String.valueOf(zkConf.getZkSrvConf().getTimeout());
        String ZK_CONTAINER_PATH = zNode.getPath();
        String ZK_NAMING_SERVICE = zkConf.getServices().getPath();
        String SHUTDOWN_NODE = zkConf.getShutdown().getPath();
        String CONF_NODE = zNode.getConfNodePath();
        // create a string with all the key-value pairs (env vars)
        conBootEnv = "";
        // set the arguments for the container boot command
        conBootArgs = String.format("%s %s %s %s %s %s", ZK_HOSTS, ZK_SESSION_TIMEOUT,
                ZK_CONTAINER_PATH, ZK_NAMING_SERVICE, SHUTDOWN_NODE, CONF_NODE);
        // create the boot command

        // FOR TESTING
        conBootCmd = "wget maestro.freelabs.net/maestroBroker.zip || curl maestro.freelabs.net/maestroBroker.zip; "
                + "rm -r /opt/maestro; "
                + "unzip maestroBroker.zip -d /opt; "
                + "exec java -jar /opt/maestro/bin/broker.jar " + conBootArgs;

        //  conBootCmd =  "java -jar " + BROKER_JAR_IN_CONTAINER + " " + conBootArgs;
    }

    @Override
    public CreateContainerResponse createContainer() {
        LOG.info("Creating container for service {}...", con.getName());
        // create object to process declared docker configuration
        DockerConfProcessor dcp = new DockerConfProcessor(con.getDocker());
        // get the name with which to deploy the container 
        String conName = zkConf.getDeplCons().get(con.getName());
        // boot command
        String conCmd = conBootCmd;
        // env var passed
        String[] conEnvArr = conBootEnv.split(",");
        // get network
        String netName = zkConf.getAppDefaultNetName();
        // get hostName
        String hostName = con.getName();
        // get container image
        String conImg = dcp.getImage();
        // get privileged flag
        boolean privileged = dcp.isPrivileged();
        // process volumes
        List<Volume> volList = dcp.getVolumes();
        // process volumes from
        List<VolumesFrom> volsFromList = dcp.getVolumesFrom();
        // process mount bind volumes
        List<Bind> bindList = dcp.getBindMounts(upDownPaths, zkConf);
        // process exposed ports
        List<ExposedPort> expPortList = dcp.getExosedPorts();
        // process published ports
        Ports portBindings = dcp.getPublishedPorts();
        // process publishAllPorts
        boolean publishAllPorts = dcp.areAllPortsPublished();

        // set container configuration
        CreateContainerResponse container = null;
        while (container == null) {
            try {
                container = docker.createContainerCmd(conImg)
                        .withNetworkMode(netName)
                        .withHostName(hostName)
                        .withVolumes(volList.toArray(new Volume[0]))
                        .withVolumesFrom(volsFromList.toArray(new VolumesFrom[0]))
                        .withBinds(bindList.toArray(new Bind[0]))
                        .withExposedPorts(expPortList.toArray(new ExposedPort[0]))
                        .withPortBindings(portBindings)
                        .withPublishAllPorts(publishAllPorts)
                        .withName(conName)
                        .withCmd("/bin/sh", "-c", conCmd)
                        .withEnv(conEnvArr)
                        .withPrivileged(privileged)
                        .exec();
            } catch (ConflictException ex) {
                // container with this name already exists
                LOG.error("Something went wrong {}", ex.getMessage());
                break;
            } catch (NotFoundException ex) {
                // image not found locally
                LOG.warn("Image {} does not exist locally. Pulling from docker hub...", conImg);
                // pull image from docker hub
                boolean runSuccess = runAndRetry(() -> {
                    pullContainerImg(conImg);
                }, PULL_ATTEMPTS);
                // check if code executed successfully
                if (runSuccess) {
                    LOG.info("Image {} pulled successfully.", conImg);
                } else {
                    LOG.error("FAILED to pull image");
                    break;
                }
            }
        }
        return container;
    }

    @Override
    public void pullContainerImg(String img) {
        docker.pullImageCmd(img)
                .exec(new PullImageResultCallback())
                .awaitSuccess();
    }

    @Override
    public String startContainer(CreateContainerResponse container, String srv) {
        if (container != null) {
            // START CONTAINER
            LOG.info("Starting container for service {}...", srv);
            String id = container.getId();
            boolean runSuccess = runAndRetry(() -> {
                docker.startContainerCmd(id).exec();
            }, RETRY_ATTEMPTS);
            // check if code executed successfully
            if (runSuccess) {
                return container.getId();
            }
        }
        return null;
    }

    @Override
    public boolean stopContainer(String con, String srv) {
        try {
            docker.stopContainerCmd(con).exec();
        } catch (NotFoundException e) {
            LOG.error("FAILED to stop container for service {}. Container does NOT exist.", srv);
        }
        // confirm stop
        InspectContainerResponse inspResp = docker.inspectContainerCmd(con).exec();
        if (inspResp.getState().getRunning()) {
            LOG.error("FAILED to stop container for service {}", srv);
            return false;
        } else {
            LOG.info("Stopped container for service {}.", srv);
            return true;
        }
    }

    @Override
    public boolean restartContainer(String con, String srv) {
        boolean success = false;
        // get first start time
        InspectContainerResponse inspResp = docker.inspectContainerCmd(con).exec();
        String startTime1 = inspResp.getState().getStartedAt();
        // restart
        LOG.info("Restarting container for service {}...", srv);
        try {
            docker.restartContainerCmd(con).exec();
        } catch (NotFoundException e) {
            LOG.error("FAILED to restart container for service {}. Container does NOT exist.", srv);
        }
        // get second start time of the container
        InspectContainerResponse inspResp2 = docker.inspectContainerCmd(con).exec();
        String startTime2 = inspResp2.getState().getStartedAt();
        // confirm restart
        if (!startTime1.equals(startTime2)) {
            success = true;
        } else {
            LOG.error("FAILED to restart container for service {}", srv);
        }
        return success;
    }

    @Override
    public boolean deleteContainer(String con, String srv) {
        boolean success = false;
        LOG.info("Removing container for service {}...", srv);
        try {
            docker.removeContainerCmd(con)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            success = true;
            // confirm deletion
            docker.inspectContainerCmd(con).exec();
        } catch (NotFoundException e) {
            if (!success) {
                LOG.warn("FAILED to remove container for service {}. Container does NOT exist.", srv);
                // this is not an error so we need to re-set the flag to success
                success = true;
            }
        }

        return success;
    }

    /**
     * Interface to be used as a container for code which will be passed for
     * execution.
     */
    @FunctionalInterface
    protected interface RunCmd {

        /**
         * Runs any action.
         *
         * @throws Exception in any case of Exception.
         */
        public void run() throws Exception;
    }

    /**
     * Runs the specified code and retries in case of exception.
     *
     * @param cmd the code to run passed as a lambda.
     * @param maxAttempts maximum number of attempts to run the code in case an
     * exception is thrown.
     * @return true if execution succeeded.
     */
    protected boolean runAndRetry(RunCmd cmd, int maxAttempts) {
        boolean success = false;

        while (maxAttempts > 0) {
            try {
                cmd.run();
                success = true;
                break;
            } catch (Exception ex) {
                LOG.warn("{}. Retrying.", ex.getMessage());
            }
            maxAttempts--;
        }
        return success;
    }

    /**
     *
     * Class that provides methods to process the docker configuration from
     * application description defined.
     */
    public class DockerConfProcessor {

        private final Docker dockerConf;

        public DockerConfProcessor(Docker dockerConf) {
            this.dockerConf = dockerConf;
        }

        /**
         *
         * @return true if flag is set to container to publish all ports to
         * host.
         */
        public boolean areAllPortsPublished() {
            // process publishAllPorts
            return dockerConf.isPublishAllPorts();
        }

        /**
         *
         * @return {@link Ports Ports} object initialized with ports to be
         * published.
         */
        public Ports getPublishedPorts() {
            // process published ports
            List<PublishPort> defPubPortList = dockerConf.getPublishPort();
            Ports portBindings = new Ports();

            for (PublishPort defPubPort : defPubPortList) {
                // create exposed container port
                ExposedPort expPort;
                if (defPubPort.getProtocol() == Protocol.TCP) {
                    expPort = ExposedPort.tcp(defPubPort.getContainerPort());
                } else {
                    expPort = ExposedPort.udp(defPubPort.getContainerPort());
                }
                // create port binding
                String hostPort = (defPubPort.getHostPort() == null) ? null : String.valueOf(defPubPort.getHostPort());
                Binding bind = new Ports.Binding(defPubPort.getIp(), hostPort);
                portBindings.bind(expPort, bind);
            }
            return portBindings;
        }

        /**
         *
         * @return a list of initialized {@link ExposedPort ExposedPort}
         * objects.
         */
        public List<ExposedPort> getExosedPorts() {
            // process exposed ports
            List<ExposePort> defExpPortList = dockerConf.getExposePorts().getPort();
            List<ExposedPort> expPortList = new ArrayList<>();
            for (ExposePort defPort : defExpPortList) {
                // create new exposed port
                ExposedPort expPort;
                if (defPort.getProtocol() == Protocol.TCP) {
                    expPort = ExposedPort.tcp(defPort.getValue());
                } else {
                    expPort = ExposedPort.udp(defPort.getValue());
                }
                // add to list
                expPortList.add(expPort);
            }
            return expPortList;
        }

        /**
         *
         * @param uploadDownloadMap map with the upload and download paths in
         * case docker is a remote host, so data are uploaded to zookeeper and
         * downloaded to containers instead of binding and mounting.
         * @param zkConf the application zookeeper configuration.
         * @return a list of initialized {@link BindMnt BindMnts}.
         */
        public List<Bind> getBindMounts(Map<String, String> uploadDownloadMap, ZkConf zkConf) {
            // process mount bind volumes
            List<BindMnt> defBindMntList = dockerConf.getBindMnt();
            List<Bind> bindList = new ArrayList<>();

            // if docker is a remote host CONVERT BIND MOUNT PATHS TO UPLOAD-DOWNLOAD
            // DATA PATHS. Docker is not local to bind to host so data are uploaded
            // to zk and then downloaded by the container to the paths defined.
            // This is a transparent mechanism to handle bindMounts for remote hosts.
            if (zkConf.getpConf().getDockerRemote()) {
                // save upload and download paths to map for later use
                for (BindMnt bindMnt : defBindMntList) {
                    uploadDownloadMap.put(bindMnt.getHostPath(), bindMnt.getContainerPath());
                }
            } else {
                for (BindMnt bindMnt : defBindMntList) {
                    // create a new volume
                    Volume vol = new Volume(bindMnt.getContainerPath());
                    // create a new Bind
                    AccessMode am = bindMnt.getAccessMode().equals(net.freelabs.maestro.core.generated.AccessMode.RO) ? AccessMode.ro : AccessMode.rw;
                    Bind bind = new Bind(bindMnt.getHostPath(), vol, am);
                    // add to list
                    bindList.add(bind);
                }
            }
            return bindList;
        }

        /**
         *
         * @return a list of initialized {@link VolumesFrom VolumesFrom}
         * objects.
         */
        public List<VolumesFrom> getVolumesFrom() {
            // process volumes from
            List<String> defVolsFromList = dockerConf.getVolumesFrom();
            List<VolumesFrom> volsFromList = new ArrayList<>();
            for (String container : defVolsFromList) {
                VolumesFrom volFrom = new VolumesFrom(container);
                volsFromList.add(volFrom);
            }
            return volsFromList;
        }

        /**
         *
         * @return a list of initialized {@link Volume Volumes}.
         */
        public List<Volume> getVolumes() {
            // process volumes
            List<String> volPathList = dockerConf.getVolumes();
            List<Volume> volList = new ArrayList<>();
            for (String volPath : volPathList) {
                Volume vol = new Volume(volPath);
                volList.add(vol);
            }
            return volList;
        }

        /**
         *
         * @return true if flag is set for the container.
         */
        public boolean isPrivileged() {
            return dockerConf.isPrivileged();
        }

        /**
         *
         * @return the defined container image.
         */
        public String getImage() {
            return dockerConf.getImage();
        }

    }

}
