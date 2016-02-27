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

import org.apache.zookeeper.ZooKeeper;

/**
 *
 * The ZkExecutable interface is intended to be used to pass ZkExecutable
 * type objects that will be able to execute code on demand. It is targeted to be
 * used as a container for code and not to be implemented by a class.
 * <p>
 * The main use of this interface is to pass requests to a zookeeper handle that
 * will execute requests to a zookeeper service. This is useful when we want to
 * re-use a zookeeper handle so that we don't create un-necessary multiple
 * handles and connections which eventually will slow down the system.
 * <p>
 * The interface is designed to be used in conjuction with {@link ZkExecutor
 * ZkExecutor} interface that executes ZkExecutable type objects.
 */
@FunctionalInterface
public interface ZkExecutable {

    /**
     * Executes the provided code.
     *
     * @param zk a zookeeper handle.
     */
    public void exec(ZooKeeper zk);

}
