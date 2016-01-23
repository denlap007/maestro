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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to handle the entrypoint script for the main
 * process.
 */
public final class EntrypointHandler {

    /**
     * The path of the entrypoint script.
     */
    public final String entrypointPath;
    /**
     * The path of the updated entrypoint script.
     */
    private String updatedEntrypointPath;
    /**
     * The custom script to be appended to entrypoint.
     */
    private static final String CUSTOM_SCRIPT = " & echo \"_main_proc_pid=$!\"; wait;";
    /**
     * Used to indicate that initialization is complete and the main process is
     * spawned.
     */
    protected static final String CONTROL_STRING = "_main_proc_pid";
    /**
     * A Logger object.
     */
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(EntrypointHandler.class);
    /**
     * Possible arguments with the entrypoint script.
     */
    private List<String> entrypointArgs;
    /**
     * Flag that indicates if the entrypoint script was processed successfully.
     */
    private boolean isProcessed;
    /**
     * Flat that indicates if the entrypoint args were set.
     */
    private boolean areArgsSet;

    /**
     * Constructor.
     *
     * @param entrypointPath the path of the entrypoint script.
     */
    public EntrypointHandler(String entrypointPath) {
        this.entrypointPath = entrypointPath;
        entrypointArgs = new ArrayList<>();
    }

    /**
     * <p>
     * Processes and updates the entrypoint script.
     * <p>
     * The method makes a copy of the entrypoint script as a temporary file.
     * This is done in order to update the entrypoint script while the initial
     * script remains intact. As a result the process is transparent to the
     * user. Afterwards, the entrypoint copy is updated with a custom script.
     * Finally, the entrypoint path is updated to point to the updated copy.
     *
     */
    public void processEntrypoint() {
        if (isEntrypointOk()) {
            // set permissions for a new file
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxrwx");
            try {
                // create a temporary file and set its attributes
                Path temp = Files.createTempFile("entrypoint-", ".sh");
                temp.toFile().deleteOnExit();
                // make a copy of the entrypoint file
                Files.copy(Paths.get(entrypointPath), temp, StandardCopyOption.REPLACE_EXISTING);
                Files.setPosixFilePermissions(temp, perms);
                // customize entrypoint script by appending the custom script
                Files.write(temp, CUSTOM_SCRIPT.getBytes(Charset.forName("UTF-8")), StandardOpenOption.APPEND);
                // update the entrypoint path
                updatedEntrypointPath = temp.toString();
                // set processed to true
                isProcessed = true;
            } catch (IOException ex) {
                LOG.error("Something went wrong: " + ex);
            }
        }
    }

    /**
     * <p>
     * Checks if the entrypoint script exists and if so if it is a file.
     * <p>
     * This method is used for error checking for the entrypoint script.
     *
     * @return true if entrypoint script exists and is a file.
     */
    private boolean isEntrypointOk() {
        boolean fileExists = Files.exists(Paths.get(entrypointPath));
        boolean isFile = !Files.isDirectory(Paths.get(entrypointPath));

        if (fileExists && !isFile) {
            LOG.error("Entrypoint is a directory. File expected!");
        } else if (!fileExists) {
            LOG.error("Entrypoint script NOT FOUND!");
        }

        return (fileExists && isFile);
    }

    /**
     * Returns the status of {@link EntrypointHandler EntrypointHandler}.
     *
     * @return true if {@link EntrypointHandler EntrypointHandler} is
     * initialized and ready to be used.
     */
    public boolean isReady() {
        return areArgsSet && isProcessed;
    }

    /**
     * Sets the command to execute with the entrypoint script and possible
     * arguments.
     *
     * @param entrypointArgs arguments for the entrypoint script.
     */
    public void setEntrypointArgs(List<String> entrypointArgs) {
        this.entrypointArgs = entrypointArgs;
        areArgsSet = true;
    }

    /**
     *
     * @return the arguments of the entrypoint script.
     */
    public List<String> getEntrypointArgs() {
        return entrypointArgs;
    }

    /**
     *
     * @return the {@link #updatedEntrypointPath updatedEntrypointPath}.
     */
    public String getUpdatedEntrypointPath() {
        return updatedEntrypointPath;
    }

}
