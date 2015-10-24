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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import net.freelabs.maestro.broker.ShellCommandExecutor;
import net.freelabs.maestro.zookeeper.ZkConfig;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * Class that provides methods to start a container.
 */
public class ContainerLauncher implements Runnable {

    private final ShellCommandExecutor exec = new ShellCommandExecutor();

    private final ZkConfig zkConf;

    /**
     * A CountDownLatch with a count of one, representing the number of events
     * that need to occur before it releases all	waiting threads.
     */
    private final CountDownLatch shutdownSignal = new CountDownLatch(1);
    
    private final Logger LOG = LoggerFactory.getLogger(ContainerLauncher.class);

    public ContainerLauncher(ZkConfig zkConf) {
        this.zkConf = zkConf;
    }

    public String createCommand(String zkHosts, int zkSessionTimetout, String containerName, String imageName) {
        return null;
    }

    public void runCommand(String cmd) throws IOException, InterruptedException {
        LOG.info("Starting container!");
        String cmdOutput = exec.executeCommand(cmd);
        LOG.info(cmdOutput);
    }

    @Override
    public void run() {
        
        LOG.info("Container Launcher preparing command!");

        String cmd = "docker run --name maestroContainer busybox";

        try {
            runCommand(cmd);
            shutdownSignal.await();
        } catch (IOException ex) {
            LOG.error("Something went wrong: ", ex);
        } catch (InterruptedException ex) {
            LOG.warn("Interruption attempted: ", ex);
            Thread.currentThread().interrupt();
        }
    }

}
