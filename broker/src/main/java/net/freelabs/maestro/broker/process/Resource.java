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
package net.freelabs.maestro.broker.process;

import java.util.Arrays;
import java.util.List;

/**
 *
 * Class that represents a resource to be executed.
 *
 */
public final class Resource {
    /**
     * A resource to execute.
     */
    private String res;
    /**
     * Constructor.
     * @param res the resource.
     */
    public Resource(String res) {
        this.res = res;
    }

    /**
     *
     * @return the command and arguments that are specified in the resource.
     */
    public List<String> getResCmdArgs() {
        String [] cmdArgs = res.split(" ");
        return Arrays.asList(cmdArgs);
    }

    // Getters - Setters
    /**
     * 
     * @return the resource.
     */
    public String getRes() {
        return res;
    }
    /**
     * 
     * @return the resource description.
     */
    public String getDescription() {
        return res;
    }

    public void setRes(String res) {
        this.res = res;
    }
}
