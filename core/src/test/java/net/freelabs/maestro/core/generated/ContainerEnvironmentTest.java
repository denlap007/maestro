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
package net.freelabs.maestro.core.generated;

import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;
import org.junit.Test;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class ContainerEnvironmentTest extends TestCase {
    
    public ContainerEnvironmentTest(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of getHost_Url method, of class ContainerEnvironment.
     *
    public void testGetHost_Url() {
        System.out.println("getHost_Url");
        ContainerEnvironment instance = new ContainerEnvironmentImpl();
        String expResult = "";
        String result = instance.getHost_Url();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }/

    /**
     * Test of setHost_Url method, of class ContainerEnvironment.
     *
    public void testSetHost_Url() {
        System.out.println("setHost_Url");
        String value = "";
        ContainerEnvironment instance = new ContainerEnvironmentImpl();
        instance.setHost_Url(value);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }/

    /**
     * Test of getEnvMap method, of class ContainerEnvironment.
     */
    @Test
    public void testGetEnvMap() {
        System.out.println("getEnvMap");
        DataEnvironment obj = new DataEnvironment();
        obj.setHost_Url("testURL");
        obj.setDb_Port(55);
        
        ContainerEnvironment instance = new ContainerEnvironmentImpl();
        Map<String, String> expResult = new HashMap<>();
        expResult.put("HOST_URL", "testURL");
        expResult.put("DB_PORT", "55");
        
        Map<String, String> result = instance.getEnvMap(obj, "");
        assertEquals(expResult, result);
    }

    public class ContainerEnvironmentImpl extends ContainerEnvironment {
    }
    
}
