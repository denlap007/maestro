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

/**
 *
 * Interface designed to be implemented from classes that need to perform
 * shutdown operations.
 */
public interface Shutdown {

    /**
     * Performs all necessary operations (release resources, signal threads to
     * terminate e.t.c.) for normal shutdown.
     *
     * @param notifier
     */
    public void shutdown(ShutdownNotifier notifier);

    /**
     * Blocks and waits for shutdown.
     *
     * @param notifier
     */
    public void waitForShutdown(ShutdownNotifier notifier);

}
