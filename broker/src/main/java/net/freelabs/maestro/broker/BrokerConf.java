/*
 * Copyright (C) 2015 Dionysis Lappas <dio@freelabs.net>
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

import java.io.File;

/**
 *
 * Class that provides configuration for {@link Broker Broker} class.
 */
public class BrokerConf {
    public static final String BROKER_WORK_DIR_NAME = "broker";
    public static final String CONTAINER_CONF_FILE_NAME = "conf.json";
    public static final String BROKER_BASE_DIR_PATH = File.separator + BROKER_WORK_DIR_NAME;
    public static final String CONTAINER_CONF_FILE_PATH = BROKER_BASE_DIR_PATH + File.separator + CONTAINER_CONF_FILE_NAME;
    
}
