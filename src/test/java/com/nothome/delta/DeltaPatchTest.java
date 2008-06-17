/*
 * DeltaDiffPatchTestSuite.java
 * JUnit based test
 *
 * Created on May 26, 2006, 9:06 PM
 * Copyright (c) 2006 Heiko Klein
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.nothome.delta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Heiko Klein
 */
public class DeltaPatchTest {

    private File test1File;
    private File test2File;
    
    ByteArrayOutputStream read(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            while (true) {
                int r = fis.read();
                if (r == -1) break;
                os.write(r);
            }
            return os;
        } finally {
            fis.close();
        }
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
        (new File("delta")).delete();
        (new File("patchedFile.txt")).delete();
    }

    @Test
    public void testLorem() throws IOException {
        use("lorem.txt", "lorem2.txt");
        doTest();
    }
    
    @Test
    public void testLorem2() throws IOException {
        use("lorem2.txt", "lorem.txt");
        doTest();
    }
        
    @Test
    public void testLorem3() throws IOException {
        use("lorem2.txt", "lorem2.txt");
        doTest();
    }
        
    @Test
    public void testLoremLong() throws IOException {
        use("lorem-long.txt", "lorem-long2.txt");
        doTest();
    }
    
    @Test
    public void testLoremLong2() throws IOException {
        use("lorem-long2.txt", "lorem-long.txt");
        doTest();
    }
    
    @Test
    public void testVer() throws IOException {
        use("ver1.txt", "ver2.txt");
        doTest();
    }
        
    private void doTest() throws IOException {
        File patchedFile = new File("patchedFile.txt");
        File delta = new File("delta");
        DiffWriter output = new GDiffWriter(new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(delta))));
        Delta.computeDelta(test1File, test2File, output);
        output.close();

        assertTrue(delta.exists());
        // System.out.println(read(delta).toString());

        new GDiffPatcher(test1File, delta, patchedFile);
        assertTrue(patchedFile.exists());

        assertEquals(patchedFile.length(), test2File.length());
        byte[] buf = new byte[(int) test2File.length()];
        (new FileInputStream(patchedFile)).read(buf);

        assertEquals(new String(buf), read(test2File).toString());
    }

    private void use(String f1, String f2) {
        URL l1 = getClass().getClassLoader().getResource(f1);
        URL l2 = getClass().getClassLoader().getResource(f2);
        test1File = new File(l1.getPath());
        test2File = new File(l2.getPath());
    }

}