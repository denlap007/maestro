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
package net.freelabs.maestro.core.zookeeper;

/**
 *
 * <p>
 * Functional Interface that defines a method to execute an object of type {@link ZkExecutable
 * ZkExecutable}.
 * <p>
 * This interface must be implemented from class instances that have a {@link
 * ZooKeeper ZooKeeper} handle which provides access to a zookeeper service.
 * This is necessary because the object of type
 * {@link ZkExecutable ZkExecutable} needs a zookeeper handle to execute
 * requests in order to access the zookeeper service.
 * <p>
 * The interface transforms any class instance into a proxy for zookeeper
 * requests.
 */
@FunctionalInterface
public interface ZkExecutor {

    /**
     * Executes the ZkExecutable obj.
     *
     * @param obj an object to be executed.
     */
    public void zkExec(ZkExecutable obj);

}
