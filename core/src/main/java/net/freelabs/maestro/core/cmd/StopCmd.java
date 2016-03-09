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

import java.util.List;
import net.freelabs.maestro.core.boot.ProgramConf;
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
        // the application name
        String appName = args[0];
        // flag indicating if stop command was successful
        boolean stopped = false;
        // msg 
        String msg = "";
        // initialize object to re-create application namespace
        ZkConf zkConf = new ZkConf(appName, false, pConf.getZkHosts(), pConf.getZkSessionTimeout());
        // initialize master to connect to zookeeper
        ZkMaster master = new ZkMaster(zkConf);
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
                        }
                    } else {
                        msg = String.format("NO containers-Services running. \'%s\' alredy stopped.", args[0]);
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

    public void errExit() {
        System.exit(1);
    }
}
