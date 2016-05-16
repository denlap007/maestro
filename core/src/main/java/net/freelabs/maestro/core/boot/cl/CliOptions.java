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
package net.freelabs.maestro.core.boot.cl;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;

/**
 *
 * Class whose instance defines all the command line parameters and commands for
 * the program.
 */
public class CliOptions {

    @Parameter(names = {"-h", "--help"}, description = "Show this help message.", help = true)
    private boolean help;

    @Parameter(names = {"-v", "--version"}, description = "Show program version.")
    private boolean version;

    @Parameter(names = {"-c", "--conf"}, description = "<program conf> Path of .properties file with program's configuration.", required = false)
    private String conf;

    @Parameter(names = {"-t", "--zkTimeout"}, description = "<session timeout> Zookeeper client session timeout.", required = false)
    private int zTimeout;

    @Parameter(names = {"-z", "--zkHost"}, description = "<hosts> Zookeeper host list (comma seperated host:port pairs--no space).", required = false)
    private String zHosts;

    @Parameter(names = {"-l", "--log"}, description = "<log4j properties> Path of log4j.properties file with logging configuration.", required = false)
    private String log4j;

    // Getters
    public boolean isVersion() {
        return version;
    }

    public boolean isHelp() {
        return help;
    }

    public String getConf() {
        return conf;
    }

    public void setConf(String conf) {
        this.conf = conf;
    }

    public int getzTimeout() {
        return zTimeout;
    }

    public void setzTimeout(int zTimeout) {
        this.zTimeout = zTimeout;
    }

    public String getzHosts() {
        return zHosts;
    }

    public void setzHosts(String zHosts) {
        this.zHosts = zHosts;
    }

    public String getLog4j() {
        return log4j;
    }

    public void setLog4j(String log4j) {
        this.log4j = log4j;
    }
    

    // start command
    @Parameters(commandDescription = "Start application deployment")
    public class StartCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for start command", help = true)
        private boolean help;

        @Parameter(names = {"-s", "--xmlSchema"}, description = "<schema file> Path of xml schema file.", required = false)
        private String schema;

        @Parameter(names = {"-d", "--dockerHost"}, description = "<docker uri> Docker host uri.", required = false)
        private String docker;

        @Parameter(names = {"-r", "--dockerRemote"}, description = "Flag if docker host is a remote host.", required = false)
        private Boolean dockerRemote;

        @Parameter(names = {"-x", "--xmlFile"}, description = "<app xml> Path of application description xml file.", required = false)
        private String xml;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public String getSchema() {
            return schema;
        }

        public String getDocker() {
            return docker;
        }

        public String getXml() {
            return xml;
        }

        public Boolean isDockerRemote() {
            return dockerRemote;
        }
    }

    // stop command
    @Parameters(commandDescription = "Stop deployed application")
    public class StopCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for stop command", help = true)
        private boolean help;

        @Parameter(description = "<appId> The Id of the deployed application to stop.", required = true)
        private List<String> args;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public List<String> getArgs() {
            return args;
        }
    }

    // restart command
    @Parameters(commandDescription = "Restart deployed application")
    public class RestartCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for restart command", help = true)
        private boolean help;

        @Parameter(description = "<appId> The Id of the deployed application to restart.", required = true)
        private List<String> args;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public List<String> getArgs() {
            return args;
        }
    }

    // delete command
    @Parameters(commandDescription = "Delete deployed application (zookeeper namespace and docker containers).")
    public class DeleteCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for delete command", help = true)
        private boolean help;

        @Parameter(description = "<appId> The Id of the deployed application to delete.", required = true)
        private List<String> args;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public List<String> getArgs() {
            return args;
        }
    }
}
