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
import static net.freelabs.maestro.core.broker.CoreBroker.LOG;
import net.freelabs.maestro.core.generated.BusinessContainer;
import net.freelabs.maestro.core.zookeeper.ZkConfig;

/**
 * Class that provides methods to handle initialization and bootstrapping of a
 * Business container type.
 */
public class CoreBusinessBroker extends CoreBroker {

    private final BusinessContainer businessCon;

    public CoreBusinessBroker(ZkConfig zkConf, BusinessContainer con, DockerClient dockerClient) {
        super(zkConf, con, dockerClient);
        businessCon = con;
    }

    @Override
    public String bootContainer() {
        // boot configuration
        String containerName = businessCon.getName();
        Volume volume1 = new Volume("/broker");
        // get the container description
        String conEnv = getBootEnv();

        // set environment configuration
        String runBrokerCmd = "java -jar /broker/broker.jar $ZK_HOSTS $ZK_SESSION_TIMEOUT $ZK_CONTAINER_PATH $ZK_NAMING_SERVICE $SHUTDOWN_NODE $CONF_NODE";

        // set container configuration
        CreateContainerResponse container = dockerClient.createContainerCmd(businessCon.getDockerImage())
                .withVolumes(volume1)
                .withBinds(new Bind("/home/dio/THESIS/maestro/core/src/main/resources", volume1, AccessMode.rw))
                .withCmd("/bin/sh", "-c", runBrokerCmd)
                .withName(containerName)
                .withNetworkMode("bridge")
                .withEnv(conEnv.split(","))
                .withPrivileged(true)
                .exec();

        // START CONTAINER
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.info("STARTING CONTAINER: " + containerName);

        return container.getId();
    }

    @Override
    protected String getBootEnv() {
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

        return env;
    }

    @Override
    protected void setIP(String IP) {
        businessCon.getEnvironment().setHost_Ip(IP);
    }

}
