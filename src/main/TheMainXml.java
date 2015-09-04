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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 *
 * @author Dionysis Lappas (dio@freelabs.net)
 */
public class TheMainXml {

    public static void main(String[] args) throws IOException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, InstantiationException, Exception {
        String schemaPath = "/home/dio/THESIS/maestro/xmlSchema.xsd";
        String xmlFilePath = "/home/dio/THESIS/maestro/xmlTest.xml";
        String packageName = "pack";
        String outputDir = "/home/dio/testClass/source";
        

        ConfProcessor classGen = new ConfProcessor();
        //Generate classes
        classGen.xmlToClass(schemaPath, packageName, outputDir);
        //compile
        String classpath = "/home/dio/testClass/class";
        File src = new File("/home/dio/testClass/source/pack");
        File[] srcFiles = src.listFiles();

        classGen.compile(classpath, srcFiles);

        //classGen.loadInstantiateClass(classpath + File.separator + packageName);
        classGen.addToClasspath("/home/dio/testClass/class");
        classGen.unmarshal("pack", schemaPath, xmlFilePath);

        //Method method = classGen.invokeMethod("main.ClassGenerator", "test", new ArrayList<>());
        //method.invoke(classGen);
    }

}
