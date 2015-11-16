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
package net.freelabs.maestro.core.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.NetworkSettings;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import net.freelabs.maestro.core.generated.BusinessContainer;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.generated.WebContainer;
import net.freelabs.maestro.core.handler.ContainerHandler;
import net.freelabs.maestro.core.zookeeper.ConnectionWatcher;
import net.freelabs.maestro.core.zookeeper.ZkConfig;
import net.freelabs.maestro.core.zookeeper.ZkNode;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to start a container.
 */
public class ContainerLauncher extends ConnectionWatcher implements Watcher, Runnable {

    /**
     * The docker client that will communicate with the docker deamon.
     */
    private final DockerClient dockerClient;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ContainerLauncher.class);
    /**
     * The zookeeper configuration.
     */
    private final ZkConfig zkConf;
    /**
     * The docker socket uri.
     */
    private final String dockerURI;
    /**
     * A container handler to get the containers.
     */
    private final ContainerHandler handler;

    /**
     * Constructor
     *
     * @param zkConf
     * @param handler
     * @param dockerURI the docker deamon socket uri.
     */
    public ContainerLauncher(ZkConfig zkConf, ContainerHandler handler, String dockerURI) {
        super(zkConf.getHosts(), zkConf.getSESSION_TIMEOUT());
        this.zkConf = zkConf;
        this.handler = handler;
        this.dockerURI = dockerURI;

        // create and initialize the docker client
        dockerClient = initDockerClient(dockerURI);
    }

    /**
     * Creates and initializes the docker client.
     *
     * @param dockerURI the docker deamon socket uri
     * @return
     */
    private DockerClient initDockerClient(String dockerURI) {
        DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
                .withUri(dockerURI)
                .build();

        DockerClient client = DockerClientBuilder.getInstance(config)
                .build();

        return client;
    }

    /**
     * Prints info of the docker client
     */
    public void printDockerClientInfo() {
        Info info = dockerClient.infoCmd().exec();
        System.out.println(info);
    }


    public void startContainer() {
        String ZK_HOSTS = "127.0.0.1:2181";
        String ZK_SESSION_TIMEOUT = "5000";
        String DOCKER_SOCKET_URI = "unix:///var/run/docker.sock";
        String ZK_CONTAINER_PATH = "/TestWebApp/WebContainer/web";
        String ZK_NAMING_SERVICE = "/TestWebApp/services";
        String SHUTDOWN_NODE = "/TestWebApp/shutdown";
        String CONF_NODE = "/TestWebApp/conf/web";
        String newCmd = "exec /bin/sh /broker/container_bootscript.sh";

        String script;
        script = String.format("export DOCKER_SOCKET_URI=%s; export ZK_HOSTS=%s; "
                + "export ZK_SESSION_TIMEOUT=%s;  export  ZK_CONTAINER_PATH=%s; "
                + "export ZK_NAMING_SERVICE=%s; export SHUTDOWN_NODE=%s;"
                + "export CONF_NODE=%s; %s;", DOCKER_SOCKET_URI, ZK_HOSTS,
                ZK_SESSION_TIMEOUT, ZK_CONTAINER_PATH, ZK_NAMING_SERVICE,
                SHUTDOWN_NODE, CONF_NODE, newCmd);

        Volume volume1 = new Volume("/broker");
        String containerName = "fromCode";

        CreateContainerResponse container = dockerClient.createContainerCmd("dio/minijava")
                .withVolumes(volume1)
                .withBinds(new Bind("/home/dio/THESIS/maestro/core/src/main/resources", volume1, AccessMode.rw))
                .withTty(true)
                .withStdinOpen(true)
                .withCmd("/bin/sh", "-c", script)//, "/broker/container_bootscript.sh")
                .withName(containerName)
                .withNetworkMode("host")
                .exec();

        LOG.info("STARTING CONTAINER: " + containerName);
        dockerClient.startContainerCmd(container.getId()).exec();
    }

    /**
     * Launched all the containers in a predefined order. The order is:
     * <ul>
     * <li>DATA container</li>
     * <li>BUSINESS container</li>
     * <li>WEB container</li>
     * </ul>
     * All DATA containers are launched first, then all BUSINESS containers and 
     * finally all WEB containers. 
     */
    private void launchContainer() {
        // if there is a DATA cotnainer
        if (handler.hasDataContainers() == true) {
            // get the container
            DataContainer dataCon = handler.getDataContainer();
            // launch the cotnainer
            launchContainer(dataCon);
        } else if (handler.hasBusinessContainers() == true) {
            BusinessContainer businessCon =  handler.getBusinessContainer();
            launchContainer(businessCon);
        } else if (handler.hasWebContainers() == true) {
            WebContainer webCon = handler.getWebContainer();
            launchContainer(webCon);
        }
    }
    

    private void launchContainer(Container con) {
        String containerName = con.getName();
        ZkNode node = zkConf.getZkContainers().get(containerName);
        
        String ZK_HOSTS = zkConf.getHosts();
        String ZK_SESSION_TIMEOUT = String.valueOf(zkConf.getSESSION_TIMEOUT());
        String DOCKER_SOCKET_URI = dockerURI;    
        String ZK_CONTAINER_PATH = node.getName(); 
        String ZK_NAMING_SERVICE = zkConf.getNamingServicePath();
        String SHUTDOWN_NODE = zkConf.getShutDownPath();
        String CONF_NODE = node.getConfNodePath();
        String newCmd = "exec /bin/sh /broker/container_bootscript.sh";

        String script;
        script = String.format("export DOCKER_SOCKET_URI=%s; export ZK_HOSTS=%s; "
                + "export ZK_SESSION_TIMEOUT=%s;  export  ZK_CONTAINER_PATH=%s; "
                + "export ZK_NAMING_SERVICE=%s; export SHUTDOWN_NODE=%s;"
                + "export CONF_NODE=%s; %s;", DOCKER_SOCKET_URI, ZK_HOSTS,
                ZK_SESSION_TIMEOUT, ZK_CONTAINER_PATH, ZK_NAMING_SERVICE,
                SHUTDOWN_NODE, CONF_NODE, newCmd);

        Volume volume1 = new Volume("/broker");

        CreateContainerResponse container = dockerClient.createContainerCmd("dio/minijava")
                .withVolumes(volume1)
                .withBinds(new Bind("/home/dio/THESIS/maestro/core/src/main/resources", volume1, AccessMode.rw))
                .withTty(true)
                .withStdinOpen(true)
                .withCmd("/bin/sh", "-c", script)
                .withName(containerName)
                .withNetworkMode("host")
                .exec();

         LOG.info("STARTING CONTAINER: " + containerName);
         
         dockerClient.startContainerCmd(container.getId()).exec();
         InspectContainerResponse response = dockerClient.inspectContainerCmd(container.getId()).exec();
         NetworkSettings settings = response.getNetworkSettings();
         String containerIp = settings.getIpAddress();
    }

    @Override
    public void run() {
        launchContainer();
    }

}
