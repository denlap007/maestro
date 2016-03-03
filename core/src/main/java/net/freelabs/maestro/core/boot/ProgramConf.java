/*
 * Copyright (C) 2015-2016 Dionysis Lappas (dio@freelabs.net)
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
package net.freelabs.maestro.core.boot;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that contains the program's necessary configuration.
 */
public final class ProgramConf {

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProgramConf.class);

    private final Map<String, Boolean> confMap;

    private String zkHosts;
    private int zkSessionTimeout;
    private String xmlSchemaPath;
    private String xmlFilePath;
    private String dockerURI;
    private String AppName;
    private static final String PROPERTIES_FILE_NAME = "maestro.properties";
    private static final String PROGRAM_NAME = "maestro";
    private static final String VERSION = "0.1.0";

    public ProgramConf() {
        // put configuration parameters to map
        this.confMap = new HashMap<>();
        confMap.put("zkHosts", Boolean.FALSE);
        confMap.put("zkSessionTimeout", Boolean.FALSE);
        confMap.put("xmlSchemaPath", Boolean.FALSE);
        confMap.put("xmlFilePath", Boolean.FALSE);
        confMap.put("dockerURI", Boolean.FALSE);
    }

    /**
     * Loads the configuration parameters from the properties file that were not
     * set by the user input. If the path is empty, it defaults to the directory
     * that the program was run from.
     *
     * @param path the path to the properties file.
     * @return true if the file was successfully read.
     */
    public boolean loadFromFileUnset(String path) {
        boolean loaded = true;
        // create a Properties object to handle .properties file
        Properties prop = new Properties();
        // check if path is set
        if (path.isEmpty()) {
            // if empty search to the current dir for the default properties file
            path = PROPERTIES_FILE_NAME;
        }
        // read .properties file
        try (FileInputStream input = new FileInputStream(path)) {
            // load .properties file
            prop.load(input);
            // get the properties only if they are not set by the user
            if (zkHosts == null) {
                zkHosts = prop.getProperty("zkHosts");
            }
            if (zkSessionTimeout == 0) {
                zkSessionTimeout = Integer.parseInt(prop.getProperty("zkSessionTimeout"));
            }
            if (xmlSchemaPath == null) {
                xmlSchemaPath = prop.getProperty("xmlSchemaPath");
            }
            if (xmlFilePath == null) {
                xmlFilePath = prop.getProperty("xmlFilePath");
            }
            if (dockerURI == null) {
                dockerURI = prop.getProperty("docker.io.url");
            }
        } catch (IOException ex) {
            loaded = false;
        }

        return loaded;
    }

    /**
     * Loads the properties file with the program's configuration. If the path
     * is empty, it defaults to the directory that the program was run from. In
     * that case, the program searches for the file with name
     * "maestro.properties".
     *
     * @param path the path to the properties file with the configuration.
     * @return true if the file was loaded and configuration read successfully.
     */
    public boolean loadFromFile(String path) {
        boolean loaded = true;
        // create a Properties object to handle .properties file
        Properties prop = new Properties();
        // check if path is set
        if (path.isEmpty()) {
            // if empty search to the current dir for the default properties file
            path = PROPERTIES_FILE_NAME;
        }
        // read .properties file
        try (FileInputStream input = new FileInputStream(path)) {
            // load .properties file
            prop.load(input);
            // get the properties 
            zkHosts = prop.getProperty("zkHosts");
            zkSessionTimeout = Integer.parseInt(prop.getProperty("zkSessionTimeout"));
            xmlSchemaPath = prop.getProperty("xmlSchemaPath");
            xmlFilePath = prop.getProperty("xmlFilePath");
            dockerURI = prop.getProperty("docker.io.url");
        } catch (IOException ex) {
            loaded = false;
        }
        return loaded;
    }

    /**
     * Check if program's configuration fields where all initialized.
     *
     * @return true if all necessary configuration fields where initialized.
     */
    public boolean isConfInit() {
        boolean init = zkHosts != null && zkSessionTimeout != 0 && xmlSchemaPath != null && xmlFilePath != null && dockerURI != null;
        if (init) {
            LOG.info("Loaded configuration:");
            LOG.info("zkHosts: " + zkHosts);
            LOG.info("zkSessionTimeout: " + zkSessionTimeout);
            LOG.info("xmlSchemaPath: " + xmlSchemaPath);
            LOG.info("xmlFilePath: " + xmlFilePath);
            LOG.info("docker.io.url: " + dockerURI);
        } else {
            LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
        }
        return init;
    }

    /**
     * Sets the zookeeper hosts list, in comma separated host:port pairs.
     *
     * @param zkHosts the zookeeper hosts list.
     */
    public void setZkHosts(String zkHosts) {
        this.zkHosts = zkHosts;
    }

    /**
     * Sets the zookeeper client session timeout.
     *
     * @param zkSessionTimeout the zookeeper client session timeout.
     */
    public void setZkSessionTimeout(int zkSessionTimeout) {
        this.zkSessionTimeout = zkSessionTimeout;
    }

    /**
     * Sets the path of the xml schema to be used with the program.
     *
     * @param xmlSchemaPath he xml schema file path.
     */
    public void setXmlSchemaPath(String xmlSchemaPath) {
        this.xmlSchemaPath = xmlSchemaPath;
    }

    /**
     * Sets the path of the application description xml file that will be
     * matched against the schema.
     *
     * @param xmlFilePath the application description xml file path.
     */
    public void setXmlFilePath(String xmlFilePath) {
        this.xmlFilePath = xmlFilePath;
    }

    /**
     * Sets the docker daemon uri.
     *
     * @param dockerURI the docker daemon uri.
     */
    public void setDockerURI(String dockerURI) {
        this.dockerURI = dockerURI;
    }

    /**
     * @return the zkHosts
     */
    public String getZkHosts() {
        return zkHosts;
    }

    /**
     * @return the zkSessionTimeout
     */
    public int getZkSessionTimeout() {
        return zkSessionTimeout;
    }

    /**
     * @return the xmlSchemaPath
     */
    public String getXmlSchemaPath() {
        return xmlSchemaPath;
    }

    /**
     * @return the xmlFilePath
     */
    public String getXmlFilePath() {
        return xmlFilePath;
    }

    /**
     * @return the dockerURI
     */
    public String getDockerURI() {
        return dockerURI;
    }

    /**
     *
     * @return the program version.
     */
    public static String getVERSION() {
        return VERSION;
    }

    public static String getPROGRAM_NAME() {
        return PROGRAM_NAME;
    } 

    public String getAppName() {
        return AppName;
    }

    public void setAppName(String AppName) {
        this.AppName = AppName;
    }
}
