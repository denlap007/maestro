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
package net.freelabs.maestro.core.broker;

import com.github.dockerjava.api.DockerClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import static net.freelabs.maestro.core.broker.Broker.LOG;
import net.freelabs.maestro.core.handler.ContainerHandler;
import net.freelabs.maestro.core.zookeeper.ZkConf;
import net.freelabs.maestro.core.zookeeper.ZkMaster;

/**
 *
 * Class whose instances initialize and run the core Brokers that will interact
 * with containers.
 */
public final class BrokerInit {

    /**
     * An object to handle execution of operations on other threads.
     */
    private final ExecutorService executor;
    /**
     * A list of results from Brokers execution.
     */
    private final List<Future<Boolean>> execResults;
    /**
     * The handler to get container configuration from applications description.
     */
    private final ContainerHandler handler;
    /**
     * The zookeeper configuration.
     */
    protected final ZkConf zkConf;
    /**
     * The docker client that will communicate with the docker host.
     */
    protected final DockerClient docker;
    /**
     * The zookeeper master process.
     */
    private final ZkMaster master;
    /**
     * The total time (minutes) a Broker may run a task, before operation times
     * out.
     */
    private static final long TASK_TIMEOUT = 15;

    /**
     * Constructor
     *
     * @param handler object to query for container information.
     * @param zkConf the zookeeper configuration.
     * @param docker a docker client.
     * @param master the zookeeper master process.
     */
    public BrokerInit(ContainerHandler handler, ZkConf zkConf, DockerClient docker, ZkMaster master) {
        this.handler = handler;
        this.zkConf = zkConf;
        this.docker = docker;
        this.master = master;
        // create as many threads as containers
        if (handler == null) {
            executor = null;
        } else {
            executor = Executors.newFixedThreadPool(handler.getNumOfCons());
        }
        execResults = new ArrayList<>();
    }

    public boolean runStart() {
        // execute Brokers for data containers
        handler.listDataContainers().stream().forEach((con) -> {
            Broker broker = new DataBroker(zkConf, con, docker, master);
            String logMsg = String.format("Starting %s-Broker.", con.getName());
            runBroker(broker, Broker::onStart, logMsg, con.getName());
        });
        // execute Brokers for business containers
        handler.listBusinessContainers().stream().forEach((con) -> {
            Broker broker = new BusinessBroker(zkConf, con, docker, master);
            String logMsg = String.format("Starting %s-Broker.", con.getName());
            runBroker(broker, Broker::onStart, logMsg, con.getName());
        });
        // execute Brokers for web containers
        handler.listWebContainers().stream().forEach((con) -> {
            Broker broker = new WebBroker(zkConf, con, docker, master);
            String logMsg = String.format("Starting %s-Broker.", con.getName());
            runBroker(broker, Broker::onStart, logMsg, con.getName());
        });
        // do not allow new tasks wait for running to finish
        executor.shutdown();
        // await execution termination and return true if successful
        boolean success = awaitExecution();
        // shutdown executor normally or force shutdown in case of error
        shutdownExecutor();
        return success;
    }

    private void runBroker(Broker cb, Predicate<Broker> pred, String logMsg, String conName) {
        if (!logMsg.isEmpty()) {
            LOG.info(logMsg);
        }
        Future<Boolean> res = executor.submit(() -> {
            Thread.currentThread().setName(conName + "-Broker-" + "Thread");
            return pred.test(cb);
        });

        // add future object to list to check for execution result 
        execResults.add(res);
    }

    public boolean runStop() {
        // create a broker of any type
        Broker broker = new DataBroker(zkConf, null, docker, master);
        // runStop services and containers
        return broker.onStop();
    }

    public boolean runRestart() {
        boolean success;
        // stop application if necessary
        LOG.info("Checking application state.");
        // stop the application
        success = runStop();
        if (success) {
            // re-start application
            LOG.info("Restarting application.");
            // run Brokers with restart for data containers
            handler.listDataContainers().stream().forEach((con) -> {
                Broker broker = new DataBroker(zkConf, con, docker, master);
                runBroker(broker, Broker::onRestart, "", con.getName());
            });
            // run Brokers with restart Brokers for business containers
            handler.listBusinessContainers().stream().forEach((con) -> {
                Broker broker = new BusinessBroker(zkConf, con, docker, master);
                runBroker(broker, Broker::onRestart, "", con.getName());
            });
            // run Brokers with restart Brokers for web containers
            handler.listWebContainers().stream().forEach((con) -> {
                Broker broker = new WebBroker(zkConf, con, docker, master);
                runBroker(broker, Broker::onRestart, "", con.getName());
            });
            // do not allow new tasks wait for running to finish
            executor.shutdown();
            // await execution termination and return true if successful
            success = awaitExecution();
            // shutdown executor normally or force shutdown in case of error
            shutdownExecutor();
        }

        return success;
    }

    public void runUpdate() {

    }

    public boolean runDelete() {
        boolean success = false;
        // create a broker of any type
        Broker broker = new DataBroker(zkConf, null, docker, master);
        // delete containers
        LOG.info("Removing containers.");
        Map<String, String> deplCons = zkConf.getDeplCons();
        for (Map.Entry<String, String> entry : deplCons.entrySet()) {
            String defName = entry.getKey();
            String deplname = entry.getValue();
            // delete
            success = broker.deleteContainer(deplname, defName);
            if (!success) {
                break;
            }
        }

        return success;
    }

    /**
     * <p>
     * Waits the execution of tasks from {@link Broker Broker} instances to
     * complete and gets the returned results.
     * <p>
     * Method blocks.
     *
     * @return true if all executed tasks of {@link Broker Broker} instances
     * competed without errors.
     */
    private boolean awaitExecution() {
        boolean success = true;
        for (Future<Boolean> future : execResults) {
            try {
                boolean execRes = future.get(TASK_TIMEOUT, TimeUnit.MINUTES);
                success = execRes && success;
                // check result and exit if task failed
                if (!success) {
                    break;
                }
            } catch (InterruptedException ex) {
                // log the event
                LOG.warn("Thread Interrupted. Stopping");
                // set the interrupt status
                Thread.currentThread().interrupt();
                success = false;
                break;
            } catch (ExecutionException ex) {
                LOG.error("Something went wrong: {}", ex);
                success = false;
                break;
            } catch (TimeoutException ex) {
                LOG.error("Task timed out.");
                success = false;
                break;
            }
        }

        return success;
    }

    /**
     * Shuts down the executor service. If any tasks are still running, they are
     * canceled.
     */
    private void shutdownExecutor() {
        try {
            executor.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            // log the event
            LOG.warn("Thread Interrupted. Stopping");
            // set the interrupt status
            Thread.currentThread().interrupt();
        }
        if (!executor.isTerminated()) {
            LOG.warn("Canceling non-finished tasks.");
        }
        executor.shutdownNow();
    }
}
