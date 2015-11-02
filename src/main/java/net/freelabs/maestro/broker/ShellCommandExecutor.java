/*
 * Copyright (C) 2015 Dionysis Lappas (dio@freelabs.net)
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 * Class that provides method to get a Runtime object in order to run 
 * shell commands.
 */
public class ShellCommandExecutor {

    public String executeCommand(String command) throws IOException, InterruptedException {
        // Declare - Initialize valiables
        StringBuilder output = new StringBuilder();
        String line;
        Process p;
        // Execute the command
        p = Runtime.getRuntime().exec(command);
        // Wait for the command to finish
        p.waitFor();
        // Get and return the output
        BufferedReader reader
                = new BufferedReader(new InputStreamReader(p.getInputStream()));

        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        return output.toString();
    }
    
    //------------------------------ TEST --------------------------------------
    public static void main(String[] args) throws IOException, InterruptedException {
		ShellCommandExecutor obj = new ShellCommandExecutor();
		String command = "docker run --name maestroContainer busybox";
		String output = obj.executeCommand(command);
		System.out.println(output);
                Thread.sleep(15000);
	}

}
