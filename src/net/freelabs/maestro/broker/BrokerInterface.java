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
     * Gets the Container configuration after container is started.
     * @param cmdExec an object to execute the docker command.
     * @param containerName the name of the container to inspect as declared in 
     * the docker namespace.
     */
    public void inspectContainer(ShellCommandExecutor cmdExec, String containerName);
    /**
     * Registers a Broker to the naming service.
     * @param namingService the path of the Naming Service to the zookeeper 
     * hierarchical namespace.
     */
    public void registerContainer(String namingService);
    /**
     * Create the zNode for the Broker.
     * @param zkPath the path of the zNode to the zookeeper hierarchical namespace.
     */
    public void createContainer(String zkPath);
    /**
     * Runs and initializes Broker. This is a top-level method. It is used to run
     * Broker's methods in order to initialize and start.
     */
    public void runBroker();
    /**
     * Gets the container's initial configuration.
     */
    public void getInitConf();
    
}
