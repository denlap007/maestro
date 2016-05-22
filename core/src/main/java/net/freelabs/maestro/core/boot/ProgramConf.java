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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that contains the program's necessary configuration.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ProgramConf {

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProgramConf.class);
    // zookeeper conf
    private String zkHosts;
    private int zkSessionTimeout;
    // xml conf
    private String xmlSchemaPath;
    private String xmlFilePath;
    // docker conf
    private String dockerHost;
    private Boolean dockerTlsVerify;
    private String dockerCertPath;
    private String dockerConfigPath;
    private String dockerApiVersion;
    private String dockerRegistryUrl;
    private String dockerRegistryUser;
    private String dockerRegistryPass;
    private String dockerRegistryMail;
    // log4j conf
    private String log4jPropertiesPath;
    // program general conf
    private static final String PROPERTIES_FILE_NAME = "maestro.properties";
    private static final String PROGRAM_NAME = "maestro";
    private static final String VERSION = "0.1.0";

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
                zkHosts = prop.getProperty("zk.hosts");
            }
            if (zkSessionTimeout == 0) {
                zkSessionTimeout = Integer.parseInt(prop.getProperty("zk.session.timeout"));
            }
            if (xmlSchemaPath == null) {
                xmlSchemaPath = prop.getProperty("xml.schema.path");
            }
            if (xmlFilePath == null) {
                xmlFilePath = prop.getProperty("xml.file.path");
            }
            if (dockerHost == null) {
                dockerHost = prop.getProperty("docker.host");
            }
            if (dockerTlsVerify == null) {
                dockerTlsVerify = Boolean.parseBoolean(prop.getProperty("docker.tls.verify"));
            }
            if (dockerCertPath == null) {
                dockerCertPath = prop.getProperty("docker.cert.path");
            }
            if (dockerConfigPath == null) {
                dockerConfigPath = prop.getProperty("docker.config");
            }
            if (dockerApiVersion == null) {
                dockerApiVersion = prop.getProperty("docker.api.version");
            }
            if (dockerRegistryUrl == null) {
                dockerRegistryUrl = prop.getProperty("docker.registry.url");
            }
            if (dockerRegistryUser == null) {
                dockerRegistryUser = prop.getProperty("docker.registry.username");
            }
            if (dockerRegistryPass == null) {
                dockerRegistryPass = prop.getProperty("docker.registry.password");
            }
            if (dockerRegistryMail == null) {
                dockerRegistryMail = prop.getProperty("docker.registry.email");
            }
            if (log4jPropertiesPath == null) {
                log4jPropertiesPath = prop.getProperty("log4j.properties.path");
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
            zkHosts = prop.getProperty("zk.hosts");
            zkSessionTimeout = Integer.parseInt(prop.getProperty("zk.session.timeout"));
            xmlSchemaPath = prop.getProperty("xml.schema.path");
            xmlFilePath = prop.getProperty("xml.file.path");
            dockerHost = prop.getProperty("docker.host");
            dockerTlsVerify = Boolean.parseBoolean(prop.getProperty("docker.tls.verify"));
            dockerCertPath = prop.getProperty("docker.cert.path");
            dockerConfigPath = prop.getProperty("docker.config");
            dockerApiVersion = prop.getProperty("docker.api.version");
            dockerRegistryUrl = prop.getProperty("docker.registry.url");
            dockerRegistryUser = prop.getProperty("docker.registry.username");
            dockerRegistryPass = prop.getProperty("docker.registry.password");
            dockerRegistryMail = prop.getProperty("docker.registry.email");
            log4jPropertiesPath = prop.getProperty("log4j.properties.path");
        } catch (IOException ex) {
            loaded = false;
        }
        return loaded;
    }

    /**
     * Check if program's configuration fields where all initialized.
     *
     * @return true if all necessary configuration fields where initialized.
     *
     * public boolean isConfInit() { boolean init = zkHosts != null &&
     * zkSessionTimeout != 0 && xmlSchemaPath != null && xmlFilePath != null &&
     * dockerHost != null && dockerRemote != null && dockerTlsVerify != null; if
     * (init) { LOG.info("Loaded configuration parameters:");
     * LOG.info("zk.hosts: {}", zkHosts); LOG.info("zk.session.timeout: {}",
     * zkSessionTimeout); LOG.info("xml.schema.path: {}", xmlSchemaPath);
     * LOG.info("xml.file.path: {}", xmlFilePath); LOG.info("docker.host: {}",
     * dockerHost); LOG.info("docker.remote: {}", dockerRemote);
     * LOG.info("docker.tls.verify: {}", dockerTlsVerify);
     * LOG.info("docker.cert.path: {}", dockerCertPath);
     * LOG.info("docker.config: {}", dockerConfigPath);
     * LOG.info("docker.api.version: {}", dockerApiVersion);
     * LOG.info("docker.registry.url: {}", dockerRegistryUrl);
     * LOG.info("docker.registry.username: {}", dockerRegistryUser);
     * LOG.info("docker.registry.password: {}", dockerRegistryPass);
     * LOG.info("docker.registry.email: {}", dockerRegistryMail); } else {
     * LOG.error("Program configuration NOT initialized. Check the .properties
     * file and/or user input."); } return init; }
     */
    public boolean isConfInit() {
        boolean init = zkHosts != null && zkSessionTimeout != 0 && xmlSchemaPath
                != null && xmlFilePath != null && dockerHost != null
                && dockerTlsVerify != null;
        if (init) {
            if (dockerTlsVerify == true) {
                if (dockerCertPath == null) {
                    LOG.error("A valid path for docker cert must be specified if tls is enabled.");
                    init = false;
                }
            }
        } else {
            LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
        }
        return init;
    }

    /**
     *
     * @return all docker configuration parameters.
     */
    public String[] getDockerConf() {
        String[] dConf = new String[9];
        dConf[0] = dockerHost;
        dConf[1] = String.valueOf(dockerTlsVerify);
        dConf[2] = dockerCertPath;
        dConf[3] = dockerConfigPath;
        dConf[4] = dockerApiVersion;
        dConf[5] = dockerRegistryUrl;
        dConf[6] = dockerRegistryUser;
        dConf[7] = dockerRegistryPass;
        dConf[8] = dockerRegistryMail;
        return dConf;
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
     * @param dockerHost the docker daemon uri.
     */
    public void setDockerHost(String dockerHost) {
        this.dockerHost = dockerHost;
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
     * @return the dockerHost
     */
    public String getDockerHost() {
        return dockerHost;
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

    public Boolean getDockerTlsVerify() {
        return dockerTlsVerify;
    }

    public String getDockerCertPath() {
        return dockerCertPath;
    }

    public String getDockerConfigPath() {
        return dockerConfigPath;
    }

    public String getDockerApiVersion() {
        return dockerApiVersion;
    }

    public String getDockerRegistryUrl() {
        return dockerRegistryUrl;
    }

    public String getDockerRegistryUser() {
        return dockerRegistryUser;
    }

    public String getDockerRegistryPass() {
        return dockerRegistryPass;
    }

    public String getDockerRegistryMail() {
        return dockerRegistryMail;
    }

    public void setDockerTlsVerify(Boolean dockerTlsVerify) {
        this.dockerTlsVerify = dockerTlsVerify;
    }

    public void setDockerCertPath(String dockerCertPath) {
        this.dockerCertPath = dockerCertPath;
    }

    public void setDockerConfigPath(String dockerConfigPath) {
        this.dockerConfigPath = dockerConfigPath;
    }

    public void setDockerApiVersion(String dockerApiVersion) {
        this.dockerApiVersion = dockerApiVersion;
    }

    public void setDockerRegistryUrl(String dockerRegistryUrl) {
        this.dockerRegistryUrl = dockerRegistryUrl;
    }

    public void setDockerRegistryUser(String dockerRegistryUser) {
        this.dockerRegistryUser = dockerRegistryUser;
    }

    public void setDockerRegistryPass(String dockerRegistryPass) {
        this.dockerRegistryPass = dockerRegistryPass;
    }

    public void setDockerRegistryMail(String dockerRegistryMail) {
        this.dockerRegistryMail = dockerRegistryMail;
    }

    public String getLog4jPropertiesPath() {
        return log4jPropertiesPath;
    }

    public void setLog4jPropertiesPath(String log4jPropertiesPath) {
        this.log4jPropertiesPath = log4jPropertiesPath;
    }
}
