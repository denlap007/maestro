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
package net.freelabs.maestro.broker.process.start;

import java.util.List;
import net.freelabs.maestro.broker.process.Resource;
import net.freelabs.maestro.broker.process.ResourceMapper;
import net.freelabs.maestro.core.schema.StartElem;

/**
 *
 * Class that provides methods to handle all the start resources to run.
 */
public final class StartResMapper extends ResourceMapper<StartElem, String> {

    /**
     * Constructor.
     *
     * @param preMain
     * @param postMain
     * @param main
     */
    public StartResMapper(List<StartElem> preMain, List<StartElem> postMain, String main) {
        initResources(preMain, postMain, main);
    }

    /**
     * Initializes the {@link #preMainRes preMainRes} list, {@link #postMainRes
     * postMainRes} list and {@link #mainRes mainRes}.
     */
    @Override
    public void initResources(List<StartElem> preMain, List<StartElem> postMain, String main) {
        // create preMain resource list
        preMain.stream().forEach((elem) -> {

            Resource res = new Resource(elem.getValue(), elem.isAbortOnFail());
            preMainRes.add(res);
        });
        // create postMain resource list
        postMain.stream().forEach((elem) -> {
            Resource res = new Resource(elem.getValue(), elem.isAbortOnFail());
            postMainRes.add(res);
        });
        // create main resource
        mainRes = new Resource(main, true);
    }
}
