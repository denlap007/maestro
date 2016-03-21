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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.dockerjava.api.ConflictException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.PullImageResultCallback;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.serializer.JsonSerializer;
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
     * The container id.
     */
    private String cid;
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
     */
    public Broker(ZkConf zkConf, Container con, DockerClient dockerClient, ZkMaster zkMaster) {
        this.zkConf = zkConf;
        this.con = con;
        this.docker = dockerClient;
        this.zkMaster = zkMaster;
        shutdownSignal = new CountDownLatch(1);
        if (con != null){
            zNode = zkConf.getContainers().get(con.getName());
        }else{
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
        // start the container
        cid = startContainer();
        // check for errors
        if (cid != null) {
            // get container IP
            success = onPostStart(cid);
        } else {
            LOG.error("Could NOT start container. Shutting down Broker.");
            shutdown();
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
            // update zNode configuration
            zNode.setData(JsonSerializer.serialize(con));
            // log the event
            LOG.info("Updated configuration of: {}, {}:{}", zNode.getName(), "IP", IP);
            // create zk configuration node
            createNode(zNode.getConfNodePath(), zNode.getData());
            // Sets the thread to wait until it's time to shutdown
            waitForShutdown();
            success = !zkError;
        } catch (JsonProcessingException ex) {
            LOG.error("FAILED to update container IP. ", ex);
        }
        return success;
    }

    public boolean onRestart() {
        boolean success = false;
        // restart the container with the deployed name
        String deplName = zkConf.getDeplCons().get(con.getName());
        boolean restarted = restartContainer(deplName);
        
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
                zkMaster.createShutdownNode();
                // if shutdown node was created without errors
                if (!zkMaster.isMasterError()) {
                    // wait services to stop
                    success = zkMaster.waitServicesToStop(services);
                    // confirm containers were stopped
                    if (success) {
                        // check for running containers
                        Map<String, String> runningCons = getRunningCons(zkConf.getDeplCons());
                        // if containers still running force stop
                        if (!runningCons.isEmpty()) {
                            success = stopRunningCons(runningCons);
                            // check that running cons stopped successfully
                            if (success) {
                                LOG.info("All containers stopped.");
                            }
                        }
                    }
                }
            } else {
                LOG.info("No service running. Checking containers.");
                // check for running containers even though services are not running
                Map<String, String> runningCons = getRunningCons(zkConf.getDeplCons());
                // if there are containers still running force stop
                if (!runningCons.isEmpty()) {
                    success = stopRunningCons(runningCons);
                    // check that running cons stopped successfully
                    if (success) {
                        LOG.info("All containers stopped.");
                    }
                } else {
                    success = true;
                    LOG.warn("No Containers-Services running. Application already stopped.");
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
        LOG.info("Querying docker host.");

        for (Map.Entry<String, String> entry : deplCons.entrySet()) {
            String defName = entry.getKey();
            String deplname = entry.getValue();
            try {
                InspectContainerResponse inspResp = docker.inspectContainerCmd(deplname).exec();
                // if container running add to map
                if (inspResp.getState().isRunning()) {
                    runningCons.put(defName, deplname);
                } else {
                    LOG.info("Container for service \'{}\' has stopped.", defName);
                }
            } catch (NotFoundException ex) {
                LOG.error("Container for service \'{}\' does not exist.", defName);
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
        for (Map.Entry<String, String> entry : runningCons.entrySet()) {
            String defName = entry.getKey();
            String deplname = entry.getValue();
            try {
                LOG.warn("Container for service \'{}\' is still running. Forcing stop.", defName);
                success = stopContainer(deplname);
            } catch (NotFoundException ex) {
                LOG.error("Container for service \'{}\' does not exist.", defName);
                success = false;
            }
        }
        return success;
    }

    /**
     * Updates the IP of the container.
     *
     * @param IP the container IP.
     */
    protected abstract void updateIP(String IP);

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
        InspectContainerResponse.NetworkSettings settings = response.getNetworkSettings();
        // return the cotnainer's IP
        return settings.getIpAddress();
    }

    /**
     * Creates a zNode.
     *
     * @param path the path of the zNode.
     * @param data the data of the zNode.
     */
    public void createNode(String path, byte[] data) {
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
                    break;
                case OK:
                    LOG.info("Created zNode: " + path);
                    shutdown();
                    break;
                default:
                    zkError = true;
                    LOG.error("Something went wrong: ",
                            KeeperException.create(KeeperException.Code.get(rc), path));
            }
        }
    };

    /**
     * Checks if a zNode is created.
     *
     * @param path the path of the zNode.
     * @param data the data of the zNode.
     */
    public void checkNode(String path, byte[] data) {
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
                    }
                    break;
                default:
                    zkError = true;
                    LOG.error("Something went wrong: ",
                            KeeperException.create(KeeperException.Code.get(rc), path));
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
            LOG.info("Initiating Core Broker shutdown.");
        }
    }

    public void shutdown() {
        LOG.info("Initiating Core Broker shutdown.");
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
        // create a string with all the key-value pairs
        conBootEnv = String.format("ZK_HOSTS=%s,ZK_SESSION_TIMEOUT=%s,"
                + "ZK_CONTAINER_PATH=%s,ZK_NAMING_SERVICE=%s,SHUTDOWN_NODE=%s,"
                + "CONF_NODE=%s", ZK_HOSTS, ZK_SESSION_TIMEOUT, ZK_CONTAINER_PATH,
                ZK_NAMING_SERVICE, SHUTDOWN_NODE, CONF_NODE);
        // set the arguments for the container boot command
        conBootArgs = String.format("%s %s %s %s %s %s", ZK_HOSTS, ZK_SESSION_TIMEOUT,
                ZK_CONTAINER_PATH, ZK_NAMING_SERVICE, SHUTDOWN_NODE, CONF_NODE);
        // create the boot command
        conBootCmd = "java -jar /broker/broker.jar " + conBootArgs;
    }

    @Override
    public CreateContainerResponse createContainer() {
        // initialize attributes
        // deployed name mapped to defined name
        String conName = zkConf.getDeplCons().get(con.getName());
        String conImg = con.getDockerImage();
        String[] conCmd = conBootCmd.split(" ");
        String conNetMode = "bridge";

        String[] conEnvArr = conBootEnv.split(",");

        boolean privileged = true;

        Volume volume1 = new Volume("/broker");

        // set container configuration
        CreateContainerResponse container = null;
        while (container == null) {
            try {
                container = docker.createContainerCmd(conImg)
                        .withVolumes(volume1).withBinds()
                        .withBinds(new Bind("/home/dio/THESIS/maestro/core/src/main/resources", volume1, AccessMode.rw))
                        .withName(conName)
                        .withCmd(conCmd)
                        .withNetworkMode(conNetMode)
                        .withEnv(conEnvArr)
                        .withPrivileged(privileged)
                        .exec();
            } catch (NotFoundException ex) {
                // image not found locally
                LOG.warn("Image \'{}\' does not exist locally. Pulling from docker hub.", con.getDockerImage());
                // pull image from docker hub
                boolean runSuccess = runAndRetry(() -> {
                    pullContainerImg(con.getDockerImage());
                }, PULL_ATTEMPTS);
                // check if code executed successfully
                if (runSuccess) {
                    LOG.info("Image \'{}\' pulled successfully.", con.getDockerImage());
                } else {
                    LOG.error("FAILED to pull image");
                    break;
                }
            } catch (ConflictException ex) {
                // container with this name already exists
                LOG.error("Something went wrong {}", ex.getMessage());
                break;
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
    public String startContainer() {
        CreateContainerResponse container = createContainer();

        if (container != null) {
            // START CONTAINER
            LOG.info("Starting container for service: " + con.getName());
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
    public boolean stopContainer(String con) {
        docker.stopContainerCmd(con).exec();
        // confirm stop
        InspectContainerResponse inspResp = docker.inspectContainerCmd(con).exec();
        if (inspResp.getState().isRunning()) {
            LOG.error("FAILED to stop container: {}", con);
            return false;
        } else {
            LOG.info("Stopped \'{}\'.", con);
            return true;
        }
    }

    @Override
    public boolean restartContainer(String con) {
        boolean success = false;
        // get first start time
        InspectContainerResponse inspResp = docker.inspectContainerCmd(con).exec();
        String startTime1 = inspResp.getState().getStartedAt();
        // restart
        LOG.info("Restarting: {}", con);
        docker.restartContainerCmd(con).exec();
        // get second start time of the container
        InspectContainerResponse inspResp2 = docker.inspectContainerCmd(con).exec();
        String startTime2 = inspResp2.getState().getStartedAt();
        // confirm restart
        if (!startTime1.equals(startTime2)) {
            success =  true;
        } else {
            LOG.error("FAILED to restart container: {}", con);
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

}
