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
package net.freelabs.maestro.utils;

import java.io.Console;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * Class the provides general purpose methods.
 */
public class Utils {

    /**
     * Gets the declared fields of a class and its superclass, recursively
     * calling the method.
     *
     * @param fields the retrieved fields from the class and its superclass.
     * @param type the Class object of the class to retrieve the fields.
     * @return the declared fields of the class and its superclass.
     */
    public final static Collection<Field> getAllFields(Collection<Field> fields, Class<?> type) {
        fields.addAll(Arrays.asList(type.getDeclaredFields()));

        if (type.getSuperclass() != null) {
            fields = getAllFields(fields, type.getSuperclass());
        }
        return fields;
    }

    /**
     * Prints object's classname, fields' names and values. The output format is
     * as follows:
     * <pre>
     * classname: {fieldName1: value, fieldName2: value, ...}
     * </pre>
     *
     * @param obj the object to print its classname, fields and values.
     * @return the descriptive string of the class.
     */
    public final static String toString(Object obj) {
        // Get the Class object of the object
        Class<?> cls = obj.getClass();
        // Create a list to hold the object's fields
        List<Field> list = new ArrayList<>();
        // Retrieve fields
        getAllFields(list, cls);
        // Create a string to hold all the field names and its values
        String description = "";

        for (Field field : list) {
            try {
                field.setAccessible(true);
                description = description + field.getName() + ": " + field.get(obj) + ", ";

            } catch (IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return (cls.getSimpleName() + ": {" + description + "}");
    }

    /**
     * Prints message and varargs.
     *
     * @param msg the message to print.
     * @param args the arguments to be printed.
     */
    public final static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    /**
     * Prints message and prompts user for a yes/no answer.
     *
     * @param msg the message to print.
     * @return true, if user input is 'y'.
     */
    public final static Boolean callAgain(String msg) {
        Console console = System.console();
        String input = console.readLine(msg + " (y/n): ");
        if (input.equalsIgnoreCase("y") == true || input.equalsIgnoreCase("n") == true) {
            return input.equalsIgnoreCase("y");
        } else {
            return false;
        }
    }

    /**
     * Gets the simple class name of the object.
     *
     * @param obj the object to inspect.
     * @return the simple class name of the object.
     */
    public static final String getType(Object obj) {
        Class<?> cls = obj.getClass();
        return cls.getSimpleName();
    }

}
