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

import com.sun.codemodel.JCodeModel;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsonschema2pojo.SchemaMapper;

/**
 *
 * Class that provides methods to dynamically generate Java Classes from json
 * files and do dynamic Class Loading & invocation.
 */
public class ClassGenerator {

    /**
     * Generate a new Java Class from json schema
     *
     * @param sourceFilePath the file path of the json schema to be used as
     * input.
     * @param className the name of the new Java Class to be generated.
     * @param packageName the target package that should be used for generated
     * types.
     * @param outputFilePath the file path of the generated Java Class.
     */
    public void generateClass(String sourceFilePath, String className, String packageName, String outputFilePath) {
        //The java code-generation context that should be used to generated new types
        JCodeModel codeModel = new JCodeModel();

        try {
            //Create a Url resource
            URL sourceUrl = new File(sourceFilePath).toURI().toURL();

            //Read json schema and add generated types to the given code model.
            new SchemaMapper().generate(codeModel, className, packageName, sourceUrl);

            codeModel.build(new File(outputFilePath));
        } catch (MalformedURLException ex) {
            System.err.println("---> [ERROR]: This is not a corrent URL form. Exiting");
            System.exit(1);
        } catch (IOException ex) {
            Logger.getLogger(JsonPreprocessor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Dynamically load a class based on its binary name using the ClassLoader
     * of the caller.
     *
     * @param className The binary name of the class to load.
     * @return A Class<?> object of the loaded class.
     * @throws ClassNotFoundException
     */
    public final Class<?> loadClass(final String className) throws ClassNotFoundException {
        // Load the target class using its package name
        Class<?> loadedMyClass = Class.forName(className);
        System.out.println("---> [INFO] Loaded Class: " + loadedMyClass.getName());

        return loadedMyClass;
    }

    /**
     * Dynamically load a class based on its package name using an external
     * ClassLoader.
     *
     * @param className The package name of the class to load.
     * @param initialize Defines weather the class should be initialized when
     * loaded.
     * @param classLoader A ClassLoader to load class.
     * @return A Class<?> object of the loaded class.
     * @throws ClassNotFoundException
     */
    public final Class<?> loadClass(final String className, Boolean initialize, final ClassLoader classLoader) throws ClassNotFoundException {
        // Load the target class using its package name
        Class<?> loadedMyClass = Class.forName(className, initialize, classLoader);
        System.out.println("---> [INFO] Loaded Class: " + loadedMyClass.getName());

        return loadedMyClass;
    }

    /**
     * Instantiate a class based on default constructor.
     *
     * @param classObj The Class object of the class to be instantiated.
     * @return A new instance of a class. Type is Object because the class type
     * is unknown.
     * @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    public final Object instantiateCLass(final Class<?> classObj) throws NoSuchMethodException, InstantiationException, InvocationTargetException, IllegalAccessException {
        // Create a new instance from the loaded class
        Constructor constructor = classObj.getConstructor();

        return constructor.newInstance();
    }

    /**
     * Dynamically load a class based on its package name using the ClassLoader
     * of the caller and instantiate the class based on default constructor.
     *
     * @param className The package name of the class to load.
     * @return
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    public final Object loadInstantiateClass(final String className) throws ClassNotFoundException, InstantiationException, SecurityException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        // Load the target class using its package name
        Class<?> loadedMyClass = Class.forName(className);
        System.out.println("---> [INFO] Loaded Class: " + loadedMyClass.getName());

        // Create a new instance from the loaded class
        Constructor constructor = loadedMyClass.getConstructor();
        System.out.println("---> [INFO] Created an object of Class: " + loadedMyClass.getName());

        return constructor.newInstance();
    }

    /* public void loadClasses(String dir) throws MalformedURLException, ClassNotFoundException {
     File folder = new File(dir);
     File[] listOfFiles = folder.listFiles();
        
     URL url = folder.toURI().toURL();

     URL[] urls = {url};
     URLClassLoader cl = URLClassLoader.newInstance(urls);

     for (File file: listOfFiles) {
     System.out.println(file.getName());
     if (file.isDirectory() || !file.getName().endsWith(".java")) {
     continue;
     }
     // -5 because of .java extension
     String className = file.getName().substring(0, file.getName().length() - 5);
     className = className.replace('/', '.');
     System.out.println(className);
     Class<?> c = cl.loadClass(className);
     }
     }*/
}
