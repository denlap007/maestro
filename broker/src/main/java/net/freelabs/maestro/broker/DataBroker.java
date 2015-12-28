/*
 * Copyright (C) 2015 Dionysis Lappas <dio@freelabs.net>
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
package net.freelabs.maestro.broker;

import java.io.IOException;
import java.util.Map;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class DataBroker extends Broker {
    
     /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DataBroker.class);
    
    public DataBroker(String zkHosts, int zkSessionTimeout, String zkContainerPath, String zkNamingService, String shutdownNode, String userConfNode) {
        super(zkHosts, zkSessionTimeout, zkContainerPath, zkNamingService, shutdownNode, userConfNode);
    }
    
  @Override
    public Container deserializeContainerConf(byte[] data) {
        DataContainer con = null;
        try {
            con = JsonSerializer.deserializeToDataContainer(data);
            LOG.info("Configuration deserialized! Printing: \n {}",
                    JsonSerializer.deserializeToString(data));
        } catch (IOException ex) {
            LOG.error("De-serialization FAILED: " + ex);
        }
        return con;
    }
    
     /**
     * Starts the main process of the associated container.
     */
    @Override
    protected void startMainProcess() {
        // create a new process builder to initialize the process
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "echo test;");
        // get the environment 
        Map<String, String> env = pb.environment();
        // initialize the environment
       

        /*System.out.println("Working directory for process: " + pb.directory());
        pb.directory(new File("myDir"));
        File log = new File("log");
        pb.redirectErrorStream(true);
        pb.redirectOutput(Redirect.appendTo(log));*/
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        try {
            Process p = pb.start();
            LOG.info("Main process STARTED.");
        } catch (IOException ex) {
            LOG.error("FAILED to start main process: {}", ex);
        }
    }
}
