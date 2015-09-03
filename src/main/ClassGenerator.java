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

import com.sun.tools.xjc.api.*;
import org.xml.sax.InputSource;

import com.sun.codemodel.JCodeModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.ValidationEventLocator;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
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
    public void jsonToClass(String sourceFilePath, String className, String packageName, String outputFilePath) {
        //The java code-generation context that should be used to generated new types
        JCodeModel codeModel = new JCodeModel();

        try {
            //Create a Url resource
            URL sourceUrl = new File(sourceFilePath).toURI().toURL();

            //Read json schema and add generated types to the given code model.
            new SchemaMapper().generate(codeModel, className, packageName, sourceUrl);

            codeModel.build(new File(outputFilePath));
        } catch (MalformedURLException ex) {
            System.err.println(" [ERROR] generateClass(): This is not a corrent URL form. Exiting");
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
        System.out.println("[INFO] Loaded Class: " + loadedMyClass.getName());

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
        System.out.println("[INFO] Loaded Class: " + loadedMyClass.getName());

        return loadedMyClass;
    }

    /**
     * Instantiate a class based on default constructor.
     *
     * @param classObj the Class object of the class to be instantiated.
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
     * Dynamically load and instantiate classes from .class files.
     *
     * @param dir the directory (package) with the .class files.
     * @return an ArrayList of objects of the instantiated classes.
     * @throws MalformedURLException
     */
    public final ArrayList<Object> loadInstantiateClass(String dir) throws MalformedURLException {
        // Initialize variables
        ArrayList<Object> objs = new ArrayList<>();
        File packageDir = new File(dir);
        File parentPackageDir = new File(packageDir.getParent());
        String packageName = packageDir.getName();
        // Get the list of files in the package
        File[] files = packageDir.listFiles();
        // Convert the parent folder path to url resource
        URL url = parentPackageDir.toURI().toURL();

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
                Logger.getLogger(ClassGenerator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return objs;
    }

    /**
     * Compile a .java source file from disk.
     *
     * @param sourcePath The path to the .java source file.
     * @param classPath The path to save the compiled .class file.
     * @return True, if compile was successful.
     * @throws IOException
     */
    public final boolean compile(String sourcePath, String classPath) throws IOException {
        // Create a compiler instance
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean success;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            // Specify where to put the genereted .class files
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
                    Arrays.asList(new File(classPath)));
            // Compile the file
            success = compiler.getTask(null, fileManager, null, null, null,
                    fileManager.getJavaFileObjectsFromFiles(Arrays.asList(new File(sourcePath))))
                    .call();
        }

        // Print msgs for compilation result
        if (success == true) {
            System.out.println("[INFO] compile(): \'" + new File(sourcePath).getName() + "\' compiled SUCCESSFULLY!");
        } else {
            System.out.println("[INFO] compile(): \'" + new File(sourcePath).getName() + "\' FAILED to compile!");
        }

        return success;
    }

    /**
     * Compilation diagnostic message processing on compilation WARNING/ERROR
     */
    protected static final class MyDiagnosticListener implements DiagnosticListener<JavaFileObject> {

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            System.out.println("Line Number->" + diagnostic.getLineNumber());
            System.out.println("code->" + diagnostic.getCode());
            System.out.println("Message->"
                    + diagnostic.getMessage(Locale.ENGLISH));
            System.out.println("Source->" + diagnostic.getSource());
            System.out.println(" ");
        }
    }

    /**
     * Compile your .java source files with JavaCompiler.
     *
     * @param srcFiles the file path(s) of .java source file(s) to compile.
     * @param classPath the path to save the compiled .class file(s).
     * @return True, if compile was successful.
     */
    public final boolean compile2(String classPath, File... srcFiles) {
        // Get system compiler:
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // For compilation diagnostic message processing on compilation WARNING/ERROR
        MyDiagnosticListener c = new MyDiagnosticListener();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(c,
                Locale.ENGLISH,
                null);
        // Specify classes output folder
        Iterable<String> options = Arrays.asList("-d", classPath);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager,
                c, options, null,
                fileManager.getJavaFileObjectsFromFiles(Arrays.asList(srcFiles)));
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
     * Generate new java class (.java source file) from xml.
     *
     * @param schemaPath the path to the xml file.
     * @param packageName the package that the new class belongs to.
     * @param outputDir the directory where the generated files will be stored.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void xmlToClass(String schemaPath, String packageName, String outputDir) throws FileNotFoundException, IOException {
        // Setup schema compiler
        SchemaCompiler sc = XJC.createSchemaCompiler();
        sc.forcePackageName(packageName);

        // Setup SAX InputSource
        File schemaFile = new File(schemaPath);
        InputSource is = new InputSource(new FileInputStream(schemaFile));
        is.setSystemId(schemaFile.getAbsolutePath());

        // Parse & build
        sc.parseSchema(is);
        S2JJAXBModel model = sc.bind();
        JCodeModel jCodeModel = model.generateCode(null, null);
        jCodeModel.build(new File(outputDir));
    }

    public void test() {
        System.out.println("SUCCEDDED. invoked method based on name!");
        run(true, "test");
    }

    public void run(Boolean runAgain, String methodName, Object... args) {
        if (runAgain == true) {
            invokeMethod(methodName, args);
        }
    }

    /**
     * Get a Method object of a class method in order to invoke it.
     *
     * @param className
     * @param methodName
     * @param params
     * @return
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private void invokeMethod(String className, String methodName, Object... params) throws NoSuchMethodException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Class<?> obj;
        ArrayList<Class<?>> parameterTypes = new ArrayList<>();
        Class<?> cls = Class.forName(className);

        // Get the Class<?> object of every parameter and add it 
        // to a list.
        for (Object param : params) {
            obj = param.getClass();
            parameterTypes.add(obj);
        }

        // Get the requested method
        // Create an instance of the class that holds the method
        Object classInstance = cls.newInstance();

        Class<?>[] array = parameterTypes.toArray(new Class<?>[parameterTypes.size()]);
        Method method = classInstance.getClass().getDeclaredMethod(methodName, array);

        method.invoke(this);
    }

    /**
     * Get a Method object of a class method in order to invoke it.
     *
     * @param className
     * @param methodName
     * @param params
     * @return
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private void invokeMethod(String methodName, Object... params) {
        try {
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
            Method method = this.getClass().getDeclaredMethod(methodName, array);

            method.invoke(this);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(ClassGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Add a directory to the class path.
     * 
     * @param dir the directory to be added to the class path.
     * @throws Exception 
     */
    public void addToClasspath(String dir) throws Exception {
        File file = new File(dir);
        URL url = file.toURI().toURL();
        URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<?> urlClass = URLClassLoader.class;
        Method method = urlClass.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.setAccessible(true);
        method.invoke(urlClassLoader, new Object[]{url});
    }

    /**
     * Unmarshal an .xml document to java objects and validate against .xsd.
     * 
     * @param packageName the name of the package 
     * @param schemaPath the path of the xml schema file to validate .xml.
     * @param xmlFilePath the path of the .xml file to unmarshal.
     * @throws MalformedURLException
     * @throws ClassNotFoundException 
     */
    public void bindXmlToPojo(String packageName, String schemaPath, String xmlFilePath) throws MalformedURLException, ClassNotFoundException {
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
            //that a class is missing, explicitly specify that to JAXBContext.newInstance.
            //If you are binding classes that are generated from XJC, then the 
            //easiest way to include all the classes is to specify the generated
            //ObjectFactory class(es).
            // System.out.println(jc.toString());
            

            // create an Unmarshaller
            Unmarshaller u = jc.createUnmarshaller();

            SchemaFactory sf = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);

            try {
                Schema schema = sf.newSchema(new File(schemaPath));
                u.setSchema(schema);
                u.setEventHandler(
                        new ValidationEventHandler() {
                            // allow unmarshalling to continue even if there are errors
                            public boolean handleEvent(ValidationEvent ve) {
                                // ignore warnings
                                if (ve.getSeverity() != ValidationEvent.WARNING) {
                                    ValidationEventLocator vel = ve.getLocator();
                                    System.out.println(
                                            "Line:Col[" + vel.getLineNumber()
                                            + ":" + vel.getColumnNumber()
                                            + "]:" + ve.getMessage());
                                }

                                return true;
                            }
                        });
            } catch (org.xml.sax.SAXException se) {
                System.out.println(
                        "Unable to validate due to following error.");
                se.printStackTrace();
                System.exit(1);
            }
            
            System.out.println("File to unmarshall: " + xmlFilePath);
            Object obj = u.unmarshal(new File(xmlFilePath));

            // Check unmarhsalling
            System.out.println(obj.toString());
            System.out.println(myToString(obj));

        } catch (UnmarshalException ue) {
            // The JAXB specification does not mandate how the JAXB provider
            // must behave when attempting to unmarshal invalid XML data.  In
            // those cases, the JAXB provider is allowed to terminate the 
            // call to unmarshal with an UnmarshalException.
            System.out.println("Caught UnmarshalException");
        } catch (JAXBException je) {
            je.printStackTrace();
        }
    }
    
    /**
     * Prints the class name and get() methods along their values 
     * of an object.
     * 
     * @param obj the object to print info.
     * @return 
     */
    public String myToString(Object obj){
        String className; 
        String methodsString = "";
        List<Method> methodsList = new ArrayList<>();
        
        // Get class name of object
        Class<?> classObj = obj.getClass();
        className = classObj.getName();
        
        // Get public get methods from class
        Method[] methods = classObj.getMethods();
        for (Method m : methods){
            if (m.getName().startsWith("get"))
            try {
                methodsString = methodsString + m.getName() + ": " + m.invoke(obj) +", ";
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(ClassGenerator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return (className + ": {" +methodsString+ "}");
        
        
    }

}
