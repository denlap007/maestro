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
import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.freelabs.maestro.core.boot.ProgramConf;
import net.freelabs.maestro.core.docker.DockerInitializer;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public final class StopCmd extends Command {

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
     * Flag that indicates if application's configuration was successfully
     * downloaded.
     */
    private boolean downloadedZkConf;
    /**
     * Message used in exit message.
     */
    private String errMsg = "";
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(StopCmd.class);

    /**
     * Constructor.
     *
     * @param cmdName the name of the command.
     */
    public StopCmd(String cmdName) {
        super(cmdName);
    }

    @Override
    protected void exec(ProgramConf pConf, String... args) {
        // flag indicating if stop command was successful
        boolean stopped = false;
        // initialize parameters
        init(pConf, args);
        // connect to zk
        master.connectToZk();
        // check for errors
        if (!master.isMasterError()) {
            // check if node with appID exists
            boolean exists = master.nodeExists(zkConf.getRoot().getPath());
            if (exists) {
                // download application conf
                downloadedZkConf = downloadZkConf();
                // if conf was downloaded
                if (downloadedZkConf) {
                    // register watch to services
                    List<String> services = master.watchServices();
                    // if no error
                    if (services != null) {
                        // if no services
                        if (!services.isEmpty()) {
                            // create shutdown node
                            master.createShutdownNode();
                            // if shutdown node was created without errors
                            if (!master.isMasterError()) {
                                // wait services to stop
                                stopped = master.waitServicesToStop(services);
                                // confirm containers were stopped
                                if (stopped) {
                                    // create docker client
                                    docker = initDockerClient(zkConf.getpConf().getDockerURI());
                                    // check for running containers
                                    Map<String, String> runningCons = getRunningCons(docker, zkConf.getDeplCons());
                                    // if containers still running force stop
                                    if (!runningCons.isEmpty()) {
                                        stopRunningCons(docker, runningCons);
                                    }
                                    LOG.info("All containers stopped.");
                                }
                            }
                        } else {
                            // create docker client
                            docker = initDockerClient(zkConf.getpConf().getDockerURI());
                            // check for running containers
                            Map<String, String> runningCons = getRunningCons(docker, zkConf.getDeplCons());
                            // if containers still running force stop
                            if (!runningCons.isEmpty()) {
                                stopRunningCons(docker, runningCons);
                                stopped = true;
                            } else {
                                errMsg = String.format("No Containers-Services running. App \'%s\' already stopped.", appID);
                            }
                        }
                    }
                }
            } else {
                errMsg = String.format("Application \'%s\' does NOT exist.", appID);
            }
        }

        master.shutdownMaster();

        if (stopped) {
            LOG.info("App \'{}\' successfully STOPPED.", appID);
        } else {
            if (errMsg.isEmpty()) {
                LOG.error("FAILED to stop App \'{}\'.", appID);
            } else {
                LOG.error(errMsg);
            }

            errExit();
        }
    }

    /**
     * Stops the containers that are still in running state.
     *
     * @param docker the docker client.
     * @param runningCons map of the defined-deployed container names of the
     * containers that are running.
     */
    private void stopRunningCons(DockerClient docker, Map<String, String> runningCons) {
        for (Map.Entry<String, String> entry : runningCons.entrySet()) {
            String defName = entry.getKey();
            String deplname = entry.getValue();
            try {
                LOG.warn("Container for service \'{}\' is still running. Forcing stop.", defName);
                docker.stopContainerCmd(deplname).exec();
            } catch (NotFoundException ex) {
                LOG.error("Container for service \'{}\' does not exist.", defName);
            }
        }
    }

    /**
     * Gets a map with the defined-deployed container names of the containers
     * that are running.
     *
     * @param docker the docker client.
     * @param deplCons map with the defined-deployed container names of the
     * deployed containers.
     * @return map of the defined-deployed container names of the containers at
     * running state.
     */
    private Map<String, String> getRunningCons(DockerClient docker, Map<String, String> deplCons) {
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
     * Initializes a docker client.
     * @param dockerURI the uri of the docker host.
     * @return a docker client instance.
     */
    private DockerClient initDockerClient(String dockerURI) {
        // create a docker client 
        DockerInitializer appDocker = new DockerInitializer(dockerURI);
        return appDocker.getDockerClient();
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
        zkConf = new ZkConf(appID, false, pConf.getZkHosts(), pConf.getZkSessionTimeout());
        // initialize master to connect to zookeeper
        master = new ZkMaster(zkConf);
    }

    /**
     * Downloads node zkConf from zookeeper application tree and re-initializes
     * {@link #zkConf zkConf} with the configuration of the application as
     * deployed.
     *
     * @return true if zkConf node was successfully downloaded from zookeeper.
     */
    public boolean downloadZkConf() {
        LOG.info("Getting application configuration.");
        boolean downloaded = false;
        byte[] data = master.nodeData(zkConf.getZkConf().getPath(), null);
        // check for errors
        if (data != null) {
            try {
                zkConf = JsonSerializer.deserializeZkConf(data);
                String dataStr = JsonSerializer.deserializeToString(data);
                LOG.debug("Downloaded application configuration. Printing. {}", dataStr);
                downloaded = true;
            } catch (IOException ex) {
                LOG.error("Something went wrong: ", ex);
            }
        } else {
            LOG.error("Application data NOT found in configuration node zkConf.");
        }
        return downloaded;
    }

    /**
     * Exit with error code (1).
     */
    public void errExit() {
        System.exit(1);
    }
}
