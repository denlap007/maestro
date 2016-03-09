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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.freelabs.maestro.core.boot.ProgramConf;
import net.freelabs.maestro.core.docker.DockerInitializer;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkMaster;
import net.freelabs.maestro.core.zookeeper.ZkNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that defines a command to restart a web application.
 */
public class RestartCmd extends Command {

    private ZkMaster master;

    private ZkConf zkConf;

    private String appName;

    private DockerClient docker;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(RestartCmd.class);
    /**
     * Constructor.
     * @param cmdName the name of the command.
     */
    public RestartCmd(String cmdName) {
        super(cmdName);
    }

    @Override
    protected void exec(ProgramConf pConf, String... args) {
        // initialization
        init(pConf, args);
        // connect to zk
        master.connectToZk();
        // if no connection errors
        if (!master.isMasterError()) {
            // stop application
            boolean stopped = stop();
            if (stopped) {
                // download application configuration from zkConf node
                boolean downloaded = downloadZkConf();
                if (downloaded) {
                    // restart
                    restart();
                } else {
                    errExit();
                }
            } else {
                errExit();
            }
        }
    }

    /**
     * Restart application's containers.
     */
    private void restart() {
        // initiate RESTART
        LOG.info("Restarting...");
        // find deployed container names
        List<String> conNames = new ArrayList<>();
        Map<String, ZkNode> conMap = zkConf.getContainers();

        conMap.keySet().stream().forEach((key) -> {
            conNames.add(key);
            LOG.info("Found container: {}", key);
        });

        // restart containers
        conNames.stream().forEach((conName) -> {
            // get first start time of the container
            try {
                InspectContainerResponse inspResp = docker.inspectContainerCmd(conName).exec();
                String startTime1 = inspResp.getState().getStartedAt();
                // restart
                LOG.info("Restarting: {}", conName);
                docker.restartContainerCmd(conName).exec();
                // get second start time of the container
                InspectContainerResponse inspResp2 = docker.inspectContainerCmd(conName).exec();
                String startTime2 = inspResp2.getState().getStartedAt();
                // confirm restart
                if (!startTime1.equals(startTime2)) {
                    LOG.info("Restarted.");
                } else {
                    LOG.info("FAILED to restart.");
                    errExit();
                }
            } catch (NotFoundException ex) {
                LOG.error("Something went wrong: {}", ex.getMessage());
                errExit();
            }
        });
        LOG.info("Application \'{}\' restarted.", appName);
        master.shutdownMaster();
    }

    /**
     * Downloads node zkConf from zookeeper application tree and re-initializes
     * {@link #zkConf zkConf} with the configuration of the application as
     * deployed.
     *
     * @return true if zkConf node was successfully downloaded from zookeeper.
     */
    public boolean downloadZkConf() {
        boolean downloaded = false;
        byte[] data = master.nodeData(zkConf.getZkConf().getPath(), null);
        // check for errors
        if (data != null) {
            try {
                zkConf = JsonSerializer.deserializeZkConf(data);
                String dataStr = JsonSerializer.deserializeToString(data);
                LOG.info("Downloaded application configuration. Printing. {}", dataStr);
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
     * Initializes necessary objects.
     *
     * @param pConf program's configuration.
     * @param args arguments defined in command line.
     */
    private void init(ProgramConf pConf, String... args) {
        // the application to restart
        appName = args[0];
        // create a docker client 
        DockerInitializer appDocker = new DockerInitializer(pConf.getDockerURI());
        docker = appDocker.getDockerClient();
        // initialize object to re-create application namespace
        zkConf = new ZkConf(appName, false, pConf.getZkHosts(), pConf.getZkSessionTimeout());
        // initialize master to connect to zookeeper
        master = new ZkMaster(zkConf);
    }

    /**
     * Stops a web app that is running.
     *
     * @return true if web app stopped.
     */
    private boolean stop() {
        boolean stopped = false;

        boolean exists = master.nodeExists(zkConf.getRoot().getPath());
        if (exists) {
            LOG.info("Application found.");
            // register watch to services
            List<String> services = master.watchServices();
            // if no error
            if (services != null) {
                // if no services
                if (!services.isEmpty()) {
                    LOG.info("Stopping...");
                    // create shutdown node
                    master.createShutdownNode();
                    // make sure shutdown node was created without errors
                    if (!master.isMasterError()) {
                        // wait services to stop
                        stopped = master.waitServicesToStop(services);
                        if (stopped) {
                            LOG.info("Application stopped.");
                        } else {
                            LOG.error("FAILED to stop application: ", appName);
                        }
                    }
                } else {
                    LOG.info("Application already stopped.");
                    stopped = true;
                }
            }
        } else {
            LOG.error("Application: \'{}\' does NOT exist.", appName);
        }

        return stopped;
    }

    /**
     * Exits program with error code (1), exit due to error.
     */
    public void errExit() {
        LOG.error("Restart of \'{}\' FAILED. Exiting...", appName);
        System.exit(1);
    }

}
