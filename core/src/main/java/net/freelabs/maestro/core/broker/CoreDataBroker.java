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

import net.freelabs.maestro.core.zookeeper.ZkExecutor;
import com.github.dockerjava.api.ConflictException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.PullImageResultCallback;
import static net.freelabs.maestro.core.broker.CoreBroker.LOG;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.zookeeper.ZkConfig;

/**
 *
 * Class that provides methods to handle initialization and bootstrapping of a
 * Data container type.
 */
public class CoreDataBroker extends CoreBroker {

    /**
     * The container description.
     */
    private final DataContainer dataCon;
    /**
     * Max attempts to pull an image from docker hub.
     */
    private static final int PULL_ATTEMPTS = 2;
    /**
     * The arguments used with the boot command to boot the container.
     */
    private String bootArgs;
    /**
     * The command that boots the container.
     */
    private String bootCmd;

    /**
     * Constructor.
     *
     * @param zkConf the zookeeper configuration.
     * @param con the container object.
     * @param dockerClient an instance of a docker client.
     */
    public CoreDataBroker(ZkConfig zkConf, DataContainer con, DockerClient dockerClient, ZkExecutor zkClient) {
        super(zkConf, con, dockerClient, zkClient);
        dataCon = con;
    }
    
        @Override
    protected CreateContainerResponse createContainer() {
        Volume volume1 = new Volume("/broker");
        // get boot arguments
        String conEnv = createBootEnv();
        // create the boot command
        bootCmd = "java -jar /broker/broker.jar " + bootArgs;

        // set container configuration
        CreateContainerResponse container = null;
        while (container == null) {
            try {
                container = dockerClient.createContainerCmd(dataCon.getDockerImage())
                        .withVolumes(volume1).withBinds()
                        .withBinds(new Bind("/home/dio/THESIS/maestro/core/src/main/resources", volume1, AccessMode.rw))
                        .withCmd(bootCmd.split(" "))
                        .withName(dataCon.getName())
                        .withNetworkMode("bridge")
                        .withEnv(conEnv.split(","))
                        .withPrivileged(true)
                        .exec();
            } catch (NotFoundException ex) {
                // image not found locally
                LOG.warn("Image \'{}\' does not exist locally. Pulling from docker hub.", dataCon.getDockerImage());
                // pull image from docker hub
                boolean runSuccess = runAndRetry(() -> {
                    dockerClient.pullImageCmd(dataCon.getDockerImage())
                            .exec(new PullImageResultCallback())
                            .awaitSuccess();
                }, PULL_ATTEMPTS);
                // check if code executed successfully
                if (runSuccess) {
                    LOG.info("Image \'{}\' pulled successfully.", dataCon.getDockerImage());
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
    public String bootContainer() {
        CreateContainerResponse container = createContainer();

        if (container != null) {
            // START CONTAINER
            LOG.info("STARTING CONTAINER: " + dataCon.getName());
            String id = container.getId();
            boolean runSuccess = runAndRetry(() -> {
                dockerClient.startContainerCmd(id).exec();
            }, 3);
            // check if code executed successfully
            if (runSuccess) {
                return container.getId();
            }
        }
        return null;
    }

    @Override
    protected String createBootEnv() {
        // set boot environment configuration
        String ZK_HOSTS = zkConf.getHosts();
        String ZK_SESSION_TIMEOUT = String.valueOf(zkConf.getSESSION_TIMEOUT());
        String ZK_CONTAINER_PATH = zNode.getPath();
        String ZK_NAMING_SERVICE = zkConf.getNamingServicePath();
        String SHUTDOWN_NODE = zkConf.getShutDownPath();
        String CONF_NODE = zNode.getConfNodePath();
        // create a string with all the key-value pairs
        String env = String.format("ZK_HOSTS=%s,ZK_SESSION_TIMEOUT=%s,"
                + "ZK_CONTAINER_PATH=%s,ZK_NAMING_SERVICE=%s,SHUTDOWN_NODE=%s,"
                + "CONF_NODE=%s", ZK_HOSTS, ZK_SESSION_TIMEOUT, ZK_CONTAINER_PATH,
                ZK_NAMING_SERVICE, SHUTDOWN_NODE, CONF_NODE);
        // set the arguments for the container boot command
        bootArgs = String.format("%s %s %s %s %s %s", ZK_HOSTS, ZK_SESSION_TIMEOUT,
                ZK_CONTAINER_PATH, ZK_NAMING_SERVICE, SHUTDOWN_NODE, CONF_NODE);

        return env;
    }

    @Override
    protected void setIP(String IP) {
        dataCon.getEnvironment().setHost_IP(IP);
    }
}
