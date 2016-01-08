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
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import net.freelabs.maestro.core.generated.WebContainer;
import net.freelabs.maestro.core.zookeeper.ZkConfig;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class CoreWebBroker extends CoreBroker{
    private final WebContainer webCon; 

    public CoreWebBroker(ZkConfig zkConf, WebContainer con, DockerClient dockerClient) {
        super(zkConf, con, dockerClient);
        webCon = con;
    }

     @Override
    public String bootContainer() {
        // boot configuration
        String containerName = webCon.getName();

        // set environment configuration
        String ZK_HOSTS = zkConf.getHosts();
        String ZK_SESSION_TIMEOUT = String.valueOf(zkConf.getSESSION_TIMEOUT());
        String ZK_CONTAINER_PATH = zNode.getPath();
        String ZK_NAMING_SERVICE = zkConf.getNamingServicePath();
        String SHUTDOWN_NODE = zkConf.getShutDownPath();
        String CONF_NODE = zNode.getConfNodePath();

        Volume volume1 = new Volume("/broker");

        String environment = String.format("ZK_HOSTS=%s,ZK_SESSION_TIMEOUT=%s,"
                + "ZK_CONTAINER_PATH=%s,ZK_NAMING_SERVICE=%s,SHUTDOWN_NODE=%s,"
                + "CONF_NODE=%s,TEST=", ZK_HOSTS, ZK_SESSION_TIMEOUT, ZK_CONTAINER_PATH,
                ZK_NAMING_SERVICE, SHUTDOWN_NODE, CONF_NODE);

        String runBrokerCmd = "java -jar /broker/broker.jar $ZK_HOSTS $ZK_SESSION_TIMEOUT $ZK_CONTAINER_PATH $ZK_NAMING_SERVICE $SHUTDOWN_NODE $CONF_NODE";

        // set container configuration
        CreateContainerResponse container = dockerClient.createContainerCmd(webCon.getDockerImage())
                .withVolumes(volume1)
                .withBinds(new Bind("/home/dio/THESIS/maestro/core/src/main/resources", volume1, AccessMode.rw))
                .withCmd("/bin/sh", "-c", runBrokerCmd)
                .withName(containerName)
                .withNetworkMode("bridge")
                .withEnv(environment.split(","))
                .withPrivileged(true)
                .exec();

        // START CONTAINER
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.info("STARTING CONTAINER: " + containerName);

        return container.getId();
    }
    
}
