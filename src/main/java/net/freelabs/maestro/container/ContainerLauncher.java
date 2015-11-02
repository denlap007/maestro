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
package net.freelabs.maestro.container;


import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import net.freelabs.maestro.zookeeper.ConnectionWatcher;
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
     * Constructor
     *
     * @param zkHosts the zookeeper hosts.
     * @param zkSessionTimeout the zookeeper client session timeout.
     * @param dockerURI the docker deamon socket uri.
     */
    public ContainerLauncher(String zkHosts, int zkSessionTimeout, String dockerURI) {
        super(zkHosts, zkSessionTimeout);

        // create and initialize the docker client
        dockerClient = initDockerClient(dockerURI);
    }

    /**
     * Creates and initializes the docker client.
     * @param dockerURI the docker deamon socket uri
     * @return 
     */
    public final DockerClient initDockerClient(String dockerURI) {
        DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
                .withUri(dockerURI)
                .build();

        DockerClient client = DockerClientBuilder.getInstance(config)
                .build();

        return client;
    }

    public void testDockerClient() {
        Info info = dockerClient.infoCmd().exec();
        System.out.println(info);
    }

    public void launchDataContainer() {

    }

    public void launchWebContainer() {

    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    // ------------------------------ TEST -------------------------------------
    public static void main(String[] args) {
        ContainerLauncher launcher = new ContainerLauncher("127.0.0.1:218", 5000, "/var/run/docker.sock");
        launcher.testDockerClient();
        
        
        
    }

}
