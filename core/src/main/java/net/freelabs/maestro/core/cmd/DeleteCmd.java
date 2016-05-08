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
package net.freelabs.maestro.core.cmd;

import com.github.dockerjava.api.DockerClient;
import javax.xml.bind.JAXBException;
import net.freelabs.maestro.core.boot.ProgramConf;
import net.freelabs.maestro.core.broker.BrokerInit;
import net.freelabs.maestro.core.docker.DockerInitializer;
import net.freelabs.maestro.core.handler.ContainerHandler;
import net.freelabs.maestro.core.handler.NetworkHandler;
import net.freelabs.maestro.core.serializer.JAXBSerializer;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public final class DeleteCmd extends Command {

    /**
     * The master zookeeper process.
     */
    private ZkMaster master;
    /**
     * The zookeeper configuration for the deployed application.
     */
    private ZkConf zkConf;
    /**
     * The deployed application ID.
     */
    private String appID;
    /**
     * A docker client to query for state of containers.
     */
    private DockerClient docker;
    /**
     * Handles interaction with application networks.
     */
    private NetworkHandler netHandler;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DeleteCmd.class);

    /**
     * Constructor.
     *
     * @param cmdName the name of the command.
     */
    public DeleteCmd(String cmdName) {
        super(cmdName);
    }

    @Override
    protected void exec(ProgramConf pConf, String... args) {
        boolean success = false;
        // initialization
        init(pConf, args);
        // connect to zk
        master.connectToZk();
        // if no connection errors
        if (!master.isMasterError()) {
            // check if node with appID exists
            boolean exists = master.nodeExists(zkConf.getRoot().getPath());
            if (exists) {
                // download application conf
                boolean downloadedZkConf = downloadZkConf();
                // if conf was downloaded
                if (downloadedZkConf) {
                    // initialize docker client
                    initDockerClient(zkConf.getpConf().getDockerConf());
                    // create and initialize Broker initializer to act on containers
                    BrokerInit brokerInit = runBrokerInit();
                    // delete application namespace
                    success = master.cleanZkNamespace();
                    if (success) {
                        // remove containers
                        success = brokerInit.runDelete();
                        // remove default network
                        netHandler.deleteNetwork(zkConf.getAppDefaultNetName());
                    } 
                }
            } else {
                LOG.error("Application with id \'{}\' does NOT exist.", appID);
            }
        }

        master.shutdownMaster();

        if (success) {
            master.shutdownMaster();
            LOG.info("---> Application with id \'{}\' deleted.", appID);
        } else {
            errExit();
        }

    }

    /**
     * Creates and initializes the {@link BrokerInit Broker Initializer} that
     * will handle interaction with containers.
     *
     * @return an initialized instance of {@link BrokerInit BrokerInit}.
     */
    private BrokerInit runBrokerInit() {
        // create container handler
        ContainerHandler handler = new ContainerHandler(zkConf.getWebApp().getContainers());
        // create and initialize the Broker Initializer
        netHandler = new NetworkHandler(docker);
        return new BrokerInit(handler, zkConf, docker, master, null);
    }

    /**
     * Downloads node zkConf from zookeeper application tree and re-initializes
     * {@link #zkConf zkConf} with the configuration of the application as
     * deployed.
     *
     * @return true if zkConf node was successfully downloaded from zookeeper.
     */
    private boolean downloadZkConf() {
        LOG.info("Getting application configuration.");
        boolean downloaded = false;
        byte[] data = master.nodeData(zkConf.getZkConf().getPath(), null);
        // check for errors
        if (data != null) {
            try {
                zkConf = JAXBSerializer.deserializeToZkConf(data);
                String dataStr = JAXBSerializer.deserializeToString(data);
                LOG.debug("Downloaded application configuration. Printing. {}", dataStr);
                downloaded = true;
            } catch (JAXBException ex) {
                LOG.error("Something went wrong: ", ex);
            }
        } else {
            LOG.error("Application data NOT found in configuration node zkConf.");
        }
        return downloaded;
    }

    /**
     * Initializes necessary parameters.
     *
     * @param pConf program's configuration.
     * @param args arguments defined in command line.
     */
    private void init(ProgramConf pConf, String... args) {
        // the application to restart
        appID = args[0];
        // initialize object to re-create application namespace
        zkConf = new ZkConf(appID, pConf.getZkHosts(), pConf.getZkSessionTimeout());
        // initialize master to connect to zookeeper
        master = new ZkMaster(zkConf);
    }

    /**
     * Initializes a docker client.
     *
     * @param dockerURI the uri of the docker host.
     */
    private void initDockerClient(String[] dockerConf) {
        // create a docker client 
        DockerInitializer appDocker = new DockerInitializer(dockerConf);
        docker = appDocker.getDockerClient();
    }

    /**
     * Exits program with error code (1), exit due to error.
     */
    @Override
    protected void errExit() {
        LOG.error("Deletion of application with id \'{}\' FAILED. Exiting...", appID);
        System.exit(1);
    }

}
