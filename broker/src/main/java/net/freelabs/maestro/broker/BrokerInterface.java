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
package net.freelabs.maestro.broker;

/**
 * This interface defines the methods to be instantiated from a 
 * {@link net.freelabs.maestro.broker.Broker Broker}.
 */
public interface BrokerInterface {
    /**
     * Registers a Broker to the naming service.
     */
    public void registerToServices();
    /**
     * Create a zNode.
     * @param path
     * @param data
     */
    public void createZkNodeEphemeral(String path, byte[] data);
    /**
     * Runs and initializes Broker. This is a top-level method. It is used to run
     * Broker's methods in order to initialize and start.
     */
    public void runBroker();
    /**
     * Gets the container's user configuration.
     */
    public void getUserConf();
    
}
