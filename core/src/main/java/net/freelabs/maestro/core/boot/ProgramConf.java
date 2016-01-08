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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that contains the program's necessary configuration.
 */
public final class ProgramConf {
    
    private final String zkHosts;
    private final int zkSessionTimeout;
    private final String xmlSchemaPath;
    private final String xmlFilePath;
    private final String dockerURI;
    private final String workDir;
    private static final String PROPERTIES_FILE_NAME = "maestro.properties";

    /**
     * Read the configuration from a propertied file.
     * @param workDir the program's work directory.
     * @throws Exception if a FileNotFoundException or IOException is thrown.
     */
    public ProgramConf(String workDir) throws Exception {
        // create logger
        Logger LOG = LoggerFactory.getLogger(ProgramConf.class);
        // create a Properties object to handle .properties file
        Properties prop = new Properties();

        // read .properties file
        String filename = workDir + File.separator + PROPERTIES_FILE_NAME;
        try (FileInputStream input = new FileInputStream(filename)){
            // load a .properties file
            prop.load(input);
        } catch (IOException ex) {
            throw new Exception(ex);
        } 

        // get the property value and print it out
        zkHosts = prop.getProperty("zkHosts");
        zkSessionTimeout =  Integer.parseInt(prop.getProperty("zkSessionTimeout"));
        xmlSchemaPath = prop.getProperty("xmlSchemaPath");
        xmlFilePath = prop.getProperty("xmlFilePath");
        dockerURI = prop.getProperty("docker.io.url");
        this.workDir = workDir;

        LOG.info("Loaded configuration from \'maestro.properties\' file as follows:");
        LOG.info("zkHosts: " + zkHosts);
        LOG.info("zkSessionTimeout: " + zkSessionTimeout);
        LOG.info("xmlSchemaPath: " + xmlSchemaPath);
        LOG.info("xmlFilePath: " + xmlFilePath);
        LOG.info("docker.io.url: " + dockerURI);
        LOG.info("workDir: " + this.workDir);
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

    public String getWorkDir() {
        return workDir;
    }
    
}
