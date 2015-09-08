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
package net.freelabs.maestro.conf;

import com.sun.tools.xjc.api.*;
import org.xml.sax.InputSource;

import com.sun.codemodel.JCodeModel;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/**
 * Class that provides methods to generate classes from .xml, bind .xml to POJOs
 * and also dynamically compile, load, instantiate classes and invoke methods to
 * objects through reflection.
 */
public class ConfProcessor {

    /**
     * Dynamically loads a class based on its binary name using the ClassLoader
     * of the caller.
     *
     * @param className The binary name of the class to load.
     * @return A {@link java.lang.Class} object of the loaded class.
     * @throws ClassNotFoundException if the class wasn't found.
     */
    public final Class<?> loadClass(final String className) throws ClassNotFoundException {
        // Load the target class using its package name
        Class<?> loadedMyClass = Class.forName(className);
        System.out.println("[INFO] Loaded Class: " + loadedMyClass.getName());

        return loadedMyClass;
    }

    /**
     * Dynamically loads a class based on its package name using an external
     * ClassLoader.
     *
     * @param className The package name of the class to load.
     * @param initialize Defines weather the class should be initialized when
     * loaded.
     * @param classLoader A ClassLoader to load class.
     * @return A {@link java.lang.Class} object of the loaded class.
     * @throws ClassNotFoundException if the class wasn't found.
     */
    public final Class<?> loadClass(final String className, Boolean initialize, final ClassLoader classLoader) throws ClassNotFoundException {
        // Load the target class using its package name
        Class<?> loadedMyClass = Class.forName(className, initialize, classLoader);
        System.out.println("[INFO] Loaded Class: " + loadedMyClass.getName());

        return loadedMyClass;
    }

    /**
     * Instantiates a class based on default constructor.
     *
     * @param classObj the Class object of the class to be instantiated.
     * @return A new instance of a class. Type is Object because the class type
     * is unknown.
     */
    public final Object instantiateCLass(final Class<?> classObj) {
        try {
            // Create a new instance from the loaded class
            Constructor constructor = classObj.getConstructor();

            return constructor.newInstance();
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(ConfProcessor.class.getName()).log(Level.SEVERE, ex.getCause().toString());
            return null;
        }
    }

    /**
     * Dynamically loads and instantiates class(es) from .class file(s).
     *
     * @param dir the directory (package) with the .class files.
     * @return an ArrayList of objects of the instantiated classes.
     */
    public final ArrayList<Object> loadInstantiateClass(String dir) {
        // Initialize variables
        URL url = null;
        ArrayList<Object> objs = new ArrayList<>();
        File packageDir = new File(dir);
        File parentPackageDir = new File(packageDir.getParent());
        String packageName = packageDir.getName();

        // Get the list of files in the package
        File[] files = packageDir.listFiles();
        // Convert the parent folder path to url resource
        try {
            url = parentPackageDir.toURI().toURL();
        } catch (MalformedURLException ex) {
            return null;
        }

        // Get a new instance of the URLClassloader and initialize it with the urls
        URL[] urls = {url};
        URLClassLoader classLoader = URLClassLoader.newInstance(urls);

        for (File file : files) {
            if (file.isDirectory() || !file.getName().endsWith(".class")) {
                continue;
            }
            // -6 because of .class extension
            String className = file.getName().substring(0, file.getName().length() - 6);
            // Create the final class name
            className = packageName + "." + className;
            try {
                // Load class
                Class<?> classObj = classLoader.loadClass(className);
                System.out.println("[INFO] loadInstantiateClass(): LOADED class: " + className);

                // Instantiate class with the default constructor
                Constructor constructor = classObj.getConstructor();
                Object obj = constructor.newInstance();
                System.out.println("[INFO] loadInstantiateClass(): CREATED object of class: " + classObj.getName());

                // Add to list
                objs.add(obj);
            } catch (InstantiationException | SecurityException | NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | ClassNotFoundException ex) {
                Logger.getLogger(ConfProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return objs;
    }

    /**
     * Class to be used with the java compiler for diagnostic message processing
     * on compilation WARNING/ERROR.
     */
    protected static final class MyDiagnosticListener implements DiagnosticListener<JavaFileObject> {

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            System.out.println("[Compiler report]");
            System.out.println("Line Number->" + diagnostic.getLineNumber());
            System.out.println("code->" + diagnostic.getCode());
            System.out.println("Message->"
                    + diagnostic.getMessage(Locale.ENGLISH));
            System.out.println("Source->" + diagnostic.getSource());
            System.out.println(" ");
        }
    }

    /**
     * Compiles .java source files with JavaCompiler.
     *
     * @param srcPath the dir with the source files.
     * @param classPath the path to save the compiled .class file(s). A package
     * folder is generated automatically.
     * @return True, if compile was successful.
     */
    public final boolean compile(String classPath, String srcPath) {
        File src = new File(srcPath);
        File[] srcFiles;
        if (src.isDirectory() == true) {
            srcFiles = src.listFiles();
        } else {
            srcFiles = new File[]{src};
        }

        System.out.println("\t[Compiling classes]");
        // Get system compiler:
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // Diagnostic message processing on compilation
        MyDiagnosticListener c = new MyDiagnosticListener();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(c,
                Locale.ENGLISH,
                null);
        // Specify output folder for compiled classes
        Iterable<String> options = Arrays.asList("-d", classPath);
        //Initialize a compilation task
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager,
                c, options, null,
                fileManager.getJavaFileObjectsFromFiles(Arrays.asList(srcFiles)));
        // Run the task to compile
        Boolean result = task.call();

        // Print msgs for compilation result
        if (result == true) {
            for (File file : srcFiles) {
                System.out.println("[INFO] compile2(): \'" + file.getName() + "\' COMPILED.!");
            }
        } else {
            System.out.println("[INFO] compile2(): File(s) DID  NOT COMPILE!");
        }

        return result;
    }

    /**
     * Generates new java class(es) (.java source file(s)) from xml.
     *
     * @param schemaPath the path to the xml file.
     * @param packageName the package that the new class belongs to.
     * @param outputDir the directory where the generated files will be stored.
     * A package folder is generated automatically.
     * @return true, if operation succeeded.
     */
    public Boolean xmlToClass(String schemaPath, String packageName, String outputDir) {
        System.out.println("\t[Generating classes]");

        // Setup schema compiler
        SchemaCompiler sc = XJC.createSchemaCompiler();
        sc.forcePackageName(packageName);

        // Setup SAX InputSource
        File schemaFile = new File(schemaPath);
        try {
            InputSource is = new InputSource(new FileInputStream(schemaFile));
            is.setSystemId(schemaFile.getAbsolutePath());

            // Parse & build
            sc.parseSchema(is);
            S2JJAXBModel model = sc.bind();
            JCodeModel jCodeModel = model.generateCode(null, null);
            jCodeModel.build(new File(outputDir));

            return true;
        } catch (IOException ex) {
            Logger.getLogger(ConfProcessor.class.getName()).log(Level.SEVERE, ex.getMessage());
        }
        return false;
    }

    public void test() {
        System.out.println("SUCCEDDED. invoked method based on name!");
        run(this, true, "test");
    }

    public void run(Object owner, Boolean runAgain, String methodName, Object... args) {
        if (runAgain == true) {
            invokeMethod(owner, methodName, args);
        }
    }

    /**
     * Invokes specified method to specified object.
     *
     * @param methodName the name of the method to invoke.
     * @param params the type of the parameters of the method e.g. String, int
     * e.t.c.
     * @return the object returned from the invoked method.
     */
    private Object invokeMethod(Object owner, String methodName, Object... params) {
        Class<?> obj;
        ArrayList<Class<?>> parameterTypes = new ArrayList<>();

        // Get the Class<?> object of every parameter and add it
        // to a list.
        for (Object param : params) {
            obj = param.getClass();
            parameterTypes.add(obj);
        }
        //this.getClass().ge
        // Get and invoke the requested method
        Class<?>[] array = parameterTypes.toArray(new Class<?>[parameterTypes.size()]);
        try {
            Method method = owner.getClass().getDeclaredMethod(methodName, array);

            return method.invoke(owner);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(ConfProcessor.class.getName()).log(Level.SEVERE, ex.getMessage());
        }
        return null;
    }

    /**
     * Adds a directory to the class path. The specified dir must be the parent
     * of the package folder that holds the .java source files, as shown below:
     * dir |--package |--classes
     *
     * @param dir the directory which holds the package with the .java src
     * files.
     * @throws Exception
     */
    public void addToClasspath(String dir) throws Exception {
        System.out.println("\t[Adding to classpath]");
        File file = new File(dir);
        URL url = file.toURI().toURL();
        URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<URLClassLoader> urlClass = URLClassLoader.class;
        Method method = urlClass.getDeclaredMethod("addURL", URL.class);
        method.setAccessible(true);
        method.invoke(urlClassLoader, url);
        System.out.println("[INFO] addToClasspath(): Added to classpath: " + dir);
    }

    /**
     * Unmarshals an .xml document to java objects and validates it against
     * .xsd.
     *
     * @param packageName the name of the package where classes were generated
     * into.
     * @param schemaPath the path of the xml schema file to validate .xml.
     * @param xmlFilePath the path of the .xml file to unmarshal.
     * @return the root element of the unmarshalled xml schema. Null if .xml
     * file's syntax is not valid, .xml schema's syntax is not valid, .xml file
     * is not valid against .xml schema's restrictions (facets), a JAXBException
     * for another reason is thrown.
     */
    public Object unmarshal(String packageName, String schemaPath, String xmlFilePath) {
        System.out.println("\t[Unmarshalling .xml]");
        Object unmarshalled = null;
        try {
            // create a JAXBContext capable of handling classes generated into
            // the specified package
            JAXBContext jc = JAXBContext.newInstance(packageName);

            // For DEBUGGING.
            // To verify that you created JAXBContext correctly, call JAXBContext.
            // toString(). It will output the list of classes it knows. If a 
            // class is not in this list, the unmarshaller will never return an
            // instance of that class. Make you see all the classes you expect 
            // to be returned from the unmarshaller in the list. If you noticed 
            // that a class is missing, explicitly specify that to JAXBContext.newInstance.
            // If you are binding classes that are generated from XJC, then the 
            // easiest way to include all the classes is to specify the generated
            // ObjectFactory class(es).
            // System.out.println(jc.toString());
            // create an Unmarshaller
            Unmarshaller u = jc.createUnmarshaller();

            // If you want to validate your document before it is unmarshalled, 
            // JAXB lets you request validation by passing an object of the class 
            // javax.xml.validation.Schema to the Unmarshaller object. First, you
            // create this schema object by setting up a schema factory for the 
            // schema language of your choice. Then you create the Schema object
            // by calling the factory's method newSchema:
            SchemaFactory sf = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
            Schema schema = sf.newSchema(new File(schemaPath));

            // After the Unmarshaller object has been established, you pass it the schema.
            u.setSchema(schema);

            // Do the unmarshalling
            unmarshalled = u.unmarshal(new File(xmlFilePath));

        } catch (org.xml.sax.SAXException se) {
            System.err.println(
                    "Unable to validate due to the following error.");
            Logger.getLogger(ConfProcessor.class.getName()).log(Level.SEVERE, se.getMessage());
            return null;
        } catch (JAXBException ex) {
            Logger.getLogger(ConfProcessor.class.getName()).log(Level.SEVERE, ex.getCause().toString());
            return null;
        }

        // Print msgs
        String file = new File(xmlFilePath).getName();
        System.out.println("[INFO] unmarshal(): FIle '" + file + "' unmarshalled.");
        return unmarshalled;
    }

    /**
     * Prints the class name and get() methods along their values of an object.
     *
     * @param obj the object to print info.
     */
    public void myToString(Object obj) {
        String className;
        String methodsString = "";
        List<Method> methodsList = new ArrayList<>();

        // Get class name of object
        Class<?> classObj = obj.getClass();
        className = classObj.getName();

        // Get public get methods from class
        Method[] methods = classObj.getMethods();
        for (Method m : methods) {
            if (m.getName().startsWith("get")) {
                try {
                    methodsString = methodsString + m.getName() + ": " + m.invoke(obj) + ", ";
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    Logger.getLogger(ConfProcessor.class.getName()).log(Level.SEVERE, ex.getCause().toString());
                }
            }
        }

        System.out.println(className + ": {" + methodsString + "}");
    }

    /**
     * Prints message and prompts user for a yes/no answer.
     *
     * @param msg the message to print.
     * @return true, if user input is 'y'.
     */
    public Boolean callAgain(String msg) {
        Console console = System.console();
        String input = console.readLine(msg + " (y/n): ");
        if (input.equalsIgnoreCase("y") == true || input.equalsIgnoreCase("n") == false) {
            return input.equalsIgnoreCase("y") == true;
        } else {
            return false;
        }
    }

    public void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

}