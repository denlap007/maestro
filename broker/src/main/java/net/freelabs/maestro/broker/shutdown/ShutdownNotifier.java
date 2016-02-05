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
package net.freelabs.maestro.broker.shutdown;

import java.util.concurrent.CountDownLatch;

/**
 *
 * Class used for clients to register and monitor for shutdown.
 */
public final class ShutdownNotifier {

    /**
     * Latch that blocks and gets released when signaled to shutdown.
     */
    private final CountDownLatch SHUTDOWN_SIGNAL = new CountDownLatch(1);

    /**
     * <p>
     * Wait until shutDown is initiated.
     * <p>
     * The method blocks.
     *
     * @throws InterruptedException if interrupted.
     */
    public void waitForShutDown() throws InterruptedException {
        SHUTDOWN_SIGNAL.await();
    }

    /**
     * Releases the {@link #SHUTDOWN_SIGNAL SHUTDOWN} latch. Registered clients
     * waiting on this latch will unblock and initiate shutdown.
     */
    public void shutDown() {
        SHUTDOWN_SIGNAL.countDown();
    }

}
