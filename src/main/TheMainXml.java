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
package main;

import java.io.IOException;

/**
 *
 * @author Dionysis Lappas (dio@freelabs.net)
 */
public class TheMainXml {
    
    public static void main(String[] args) throws IOException {
        String schemaPath = "/home/dio/THESIS/maestro/xmlSchema.xsd";
        String packageName = "conf";
        String outputDir = "/home/dio/testClass/source";
        
        ClassGenerator classGen = new ClassGenerator();
        classGen.xmlToClass(schemaPath, packageName, outputDir);
    }
    
}
