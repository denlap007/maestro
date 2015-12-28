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
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.WebContainer;
import net.freelabs.maestro.core.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class WebBroker extends Broker {

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Broker.class);

    public WebBroker(String zkHosts, int zkSessionTimeout, String zkContainerPath, String zkNamingService, String shutdownNode, String userConfNode) {
        super(zkHosts, zkSessionTimeout, zkContainerPath, zkNamingService, shutdownNode, userConfNode);
    }


    @Override
    public Container deserializeContainerConf(byte[] data) {
        WebContainer con = null;
        try {
            con = JsonSerializer.deserializeToWebContainer(data);
            LOG.info("Configuration deserialized! Printing: \n {}",
                    JsonSerializer.deserializeToString(data));
        } catch (IOException ex) {
            LOG.error("De-serialization FAILED: " + ex);
        }
        return con;
    }

    @Override
    void startMainProcess() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
