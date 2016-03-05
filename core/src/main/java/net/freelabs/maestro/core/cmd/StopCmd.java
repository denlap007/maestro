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
import net.freelabs.maestro.core.boot.ProgramConf;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkMaster;
import org.apache.zookeeper.data.Stat;
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
        boolean stopped = false;
        ZkConf zkConf = new ZkConf(args[0], false, pConf.getZkHosts(), pConf.getZkSessionTimeout());
        // initialize master to connect to zookeeper
        ZkMaster master = new ZkMaster(zkConf);
        // connect to zk
        master.connectToZk();
        // check for errors
        if (!master.isMasterError()) {
            // check if root node exists
            Stat stat = master.nodeExists(zkConf.getRoot().getPath());
            
            if (stat != null) {
                // create shutdown node
                master.createShutdownNode();
                if (!master.isMasterError()) {
                    stopped = true;
                }
            }
        }

        try {
            // close session
            master.stop();
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Thread Interruped. Stopping.");
        }
        
        // print messages
        if (stopped){
            LOG.info("*** Stopped: {} ***", args[0]);
        }else{
            LOG.error("*** FAILED to stop: {} ***", args[0]);
        }

    }

    public void stopContainers(DockerClient docker) {
        docker.stopContainerCmd(cmdName);
    }

    public void exit() {
        System.exit(1);
    }

}
