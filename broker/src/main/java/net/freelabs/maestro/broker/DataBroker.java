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
package net.freelabs.maestro.broker;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.generated.DataEnvironment;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class DataBroker extends Broker {

    private DataContainer dataCon;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DataBroker.class);

    /**
     * Constructor.
     *
     * @param zkHosts the zookeeper hosts list.
     * @param zkSessionTimeout the client session timeout.
     * @param zkContainerPath the path of the Container to the zookeeper
     * namespace.
     * @param zkNamingService the path of the naming service to the zookeeper
     * namespace.
     * @param shutdownNode the node the signals the shutdown.
     * @param userConfNode the node with the initial container configuration.
     */
    public DataBroker(String zkHosts, int zkSessionTimeout, String zkContainerPath, String zkNamingService, String shutdownNode, String userConfNode) {
        super(zkHosts, zkSessionTimeout, zkContainerPath, zkNamingService, shutdownNode, userConfNode);
    }

    @Override
    public Container deserializeConType(byte[] data) {
        DataContainer con = null;
        try {
            con = JsonSerializer.deserializeToDataContainer(data);
            LOG.info("Configuration deserialized! Printing: \n {}",
                    JsonSerializer.deserializeToString(data));
        } catch (IOException ex) {
            LOG.error("De-serialization FAILED: " + ex);
        }
        // initialize instance var
        dataCon = con;

        return con;
    }

    /**
     * Starts the main process of the associated container.
     */
    @Deprecated
    protected void startMainProcess_OLD() {
        // create a new process builder to initialize the process
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", "/broker/data-entrypoint.sh mysqld;");
        // get the environment 
        Map<String, String> env = pb.environment();
        // initialize the environment
        Map<String, String> environment = getConEnv();
        env.putAll(environment);
        // redirect I/O/E streams to parent
        //pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError();
        Process p = null;
        // start process
        try {
            p = pb.start();
            LOG.info("STARTING Main process.");
        } catch (IOException ex) {
            LOG.error("FAILED to start main process: " + ex);
        }

        // wait for the executed script to finish
        int errCode = -1;
        
        if (p != null) {
            try {
                errCode = p.waitFor();
            } catch (InterruptedException ex) {
                LOG.warn("Interruption attempted: " + ex);
                Thread.currentThread().interrupt();
            }
        }
    }


    /**
     * Gets the environment of a DataContainer to a string.
     *
     * @return a Map with key/value pairs in the form key1=value1, key2=value2
     * e.t.c. with all the fields declared in the
     * {@link DataEnvironment DataEnvironment} class.
     */
    @Override
    protected Map<String, String> getConEnv() {
        DataEnvironment env = dataCon.getEnvironment();

        String DB_PORT = String.valueOf(env.getDb_Port());
        String DB_URL = env.getDb_Url();
        String DB_USER = env.getDb_User();
        String DB_PASS = env.getDb_Pass();
        String DB_NAME = env.getDb_Name();
        String DB_HOST = dataCon.getIP();

        Map<String, String> envMap = new HashMap<>();
        envMap.put("DB_PORT", DB_PORT);
        envMap.put("DB_URL", DB_URL);
        envMap.put("DB_USER", DB_USER);
        envMap.put("DB_PASS", DB_PASS);
        envMap.put("DB_NAME", DB_NAME);
        envMap.put("DB_HOST", DB_HOST);
        
        return envMap;
    }

}
