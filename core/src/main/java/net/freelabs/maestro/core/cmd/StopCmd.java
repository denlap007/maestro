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
import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
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

    private ZkMaster master;

    private ZkConf zkConf;

    private String appName;

    private DockerClient docker;
    /**
     * Message used in exit message.
     */
    private String msg = "";

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
            boolean exists = master.nodeExists(zkConf.getRoot().getPath());
            if (exists) {
                // register watch to services
                List<String> services = master.watchServices();
                // if no error
                if (services != null) {
                    // if no services
                    if (!services.isEmpty()) {
                        // create shutdown node
                        master.createShutdownNode();
                        // make sure shutdown node was created without errors
                        if (!master.isMasterError()) {
                            // wait services to stop
                            stopped = master.waitServicesToStop(services);
                            // confirm
                            if (stopped) {
                                confirmStop();
                            }
                        }
                    } else {
                        // check if containers of the deployed app were stopped
                        boolean consWereStopped = confirmStop();
                        if (consWereStopped) {
                            msg = "No Containers-Services running. App already stopped.";
                        }else{
                            stopped = true;
                        }
                    }
                }
            } else {
                msg = "Application does NOT exist.";
            }
        }

        master.shutdownMaster();

        if (stopped) {
            LOG.info("*** App stopped: {} ***", args[0]);
        } else {
            LOG.error("*** FAILED to stop App: {}. {} ***", args[0], msg);
            errExit();
        }
    }

    /**
     * <p>
     * Confirms that all the containers of the deployed application have stopped
     * by querying the docker daemon.
     * <p>
     * The method downloads the application configuration and extracts the
     * docker uri and creates a docker client to query the docker host. Then, it
     * obtains the list of the deployed containers for the application and check
     * their running state. If a container is still running it is forced to
     * stop.
     */
    private boolean confirmStop() {
        boolean consWereStopped = true;
        // get the application configuration
        downloadZkConf();
        // get docker uri as saved to conf
        String dockerURI = zkConf.getpConf().getDockerURI();
        // create a docker client 
        DockerInitializer appDocker = new DockerInitializer(dockerURI);
        docker = appDocker.getDockerClient();
        // get the deployed container names
        Map<String, String> deplCons = zkConf.getDeplCons();
        // iterate and check running state
        LOG.info("Querying docker host.");
        
        for (String deplname : deplCons.values()) {
            InspectContainerResponse inspResp = docker.inspectContainerCmd(deplname).exec();
            // if container running force stop
            if (inspResp.getState().isRunning()) {
                LOG.warn("Container \'{}\' is still running. Forcing stop.", deplname);
                docker.stopContainerCmd(deplname).exec();
                consWereStopped = false;
            } else {
                LOG.info("Confirming that container \'{}\' has stopped.", deplname);
            }
        }
        return consWereStopped;
    }

    /**
     * Initializes necessary objects.
     *
     * @param pConf program's configuration.
     * @param args arguments defined in command line.
     */
    private void init(ProgramConf pConf, String... args) {
        // the application to restart
        appName = args[0];
        // initialize object to re-create application namespace
        zkConf = new ZkConf(appName, false, pConf.getZkHosts(), pConf.getZkSessionTimeout());
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
        LOG.info("Downloading application configuration.");
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

    public void errExit() {
        System.exit(1);
    }
}
