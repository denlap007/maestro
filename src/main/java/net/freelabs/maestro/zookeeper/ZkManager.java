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
package net.freelabs.maestro.zookeeper;

import java.io.IOException;

/**
 *
 * @author Dionysis Lappas (dio@freelabs.net)
 */
public class ZkManager {
    /** 
     * The master process.
     */
    private final ZkMaster master;
    /** The naming service process.
     * 
     */
    private final ZkNamingService services;
    
    /**
     * Constructor
     * @param zkConf the zookeeper configuration.
     */
    public ZkManager(ZkConfig zkConf){
        // create master process
        master = new ZkMaster(zkConf);
        // create naming service
        services = new ZkNamingService(zkConf);
    }
    
    /**
     * Starts the naming service in a new thread.
     * @throws IOException in cases of network failure.
     * @throws InterruptedException if thread is interrupted.
     */
    public void startNamingService() throws IOException, InterruptedException{
        // connect to zookeeper and create a session
        services.connect();
        // Create a new thread to run the naming service
        Thread servicesThread = new Thread(services, "NamingServiceThread");
        // start the naming service
        servicesThread.start();
    }
    
    /**
     * Start the master process that will initialize zookeeper.
     * @throws IOException in cases of network failure.
     * @throws InterruptedException if thread is interrupted.
     */
    public void startMaster() throws IOException, InterruptedException{
        // connect to zookeeper and create a session
        master.connect();
        // Create a new thread to run the master 
        Thread masterThread = new Thread(master, "MasterThread");
        // start the master process
        masterThread.start();
    }
    
    
    
    
    
    
}
