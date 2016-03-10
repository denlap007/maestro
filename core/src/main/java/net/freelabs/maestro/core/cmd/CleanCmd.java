/*
 * Copyright (C) 2016 Dionysis Lappas <dio@freelabs.net>
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
package net.freelabs.maestro.core.cmd;

import net.freelabs.maestro.core.boot.ProgramConf;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public final class CleanCmd extends Command{

    public CleanCmd(String cmdName) {
        super(cmdName);
    }

    @Override
    protected void exec(ProgramConf pConf, String... args) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}