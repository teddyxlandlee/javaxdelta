/*
 * Copyright (c) 2003, 2007 s IT Solutions AT Spardat GmbH.
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
 *
 */
package at.spardat.xma.xdelta;

import com.nothome.delta.Delta;
import com.nothome.delta.DiffWriter;
import com.nothome.delta.GDiffWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


/**
 * This class calculates the binary difference of two zip files by applying {@link com.nothome.delta.Delta}
 * to all files contained in both zip files. All these binary differences are stored in the output zip file.
 * New files are simply copied to the output zip file. Additionally all files contained in the target zip
 * file are listed in <code>META-INF/file.list</code>.<p>
 * Use {@link JarPatcher} to apply the output zip file.<p>
 *
 * @author gruber
 */
@Deprecated	// XDeltaWrapper - this is legacy format
public class JarDelta {

    /**
     * Computes the binary differences of two zip files. For all files contained in source and target which
     * are not equal, the binary difference is caluclated by using
     * {@link com.nothome.delta.Delta#compute(byte[], byte[])} (com.nothome.delta.SeekableSource, InputStream, int, DiffWriter)}.
     * If the files are equal, nothing is written to the output for them.
     * Files contained only in target and files to small for {@link com.nothome.delta.Delta} are copied to output.
     * Files contained only in source are ignored.
     * At last a list of all files contained in target is written to <code>META-INF/file.list</code> in output.
     *
     * @param source the original zip file
     * @param target a modification of the original zip file
     * @param output the zip file where the patches have to be written to
     * @throws IOException if an error occures reading or writing any entry in a zip file
     */
	public void computeDelta(ZipFile source, ZipFile target, ZipOutputStream output) throws IOException {
        try {
            ByteArrayOutputStream listBytes = new ByteArrayOutputStream();
            PrintWriter list = new PrintWriter(new OutputStreamWriter(listBytes));
    		for(Enumeration<? extends ZipEntry> enumer = target.entries(); enumer.hasMoreElements();) {
    			ZipEntry targetEntry = enumer.nextElement();
                ZipEntry sourceEntry = source.getEntry(targetEntry.getName());
                list.println(targetEntry.getName());

                if(targetEntry.isDirectory()) {
                    if(sourceEntry==null) {
                        ZipEntry outputEntry = new ZipEntry(targetEntry);
                        output.putNextEntry(outputEntry);
                    }
                    continue;
                }

    			int targetSize = (int)targetEntry.getSize();
    			byte[] targetBytes = new byte[targetSize];
    			InputStream targetStream = target.getInputStream(targetEntry);
    			for (int erg=targetStream.read(targetBytes); erg<targetBytes.length; )
                    erg+=targetStream.read(targetBytes,erg,targetBytes.length-erg);
                targetStream.close();
                int chunk = Delta.DEFAULT_CHUNK_SIZE;
    			if(sourceEntry==null
                        || sourceEntry.getSize() <= chunk
                        || targetEntry.getSize() <= chunk) {  // new Entry od. alter Eintrag od. neuer Eintrag leer
    				ZipEntry outputEntry = new ZipEntry(targetEntry);
    				output.putNextEntry(outputEntry);
    				output.write(targetBytes);
    			} else {
    				int sourceSize = (int)sourceEntry.getSize();
    				byte[] sourceBytes = new byte[sourceSize];
    				InputStream sourceStream = source.getInputStream(sourceEntry);
    				for (int erg = sourceStream.read(sourceBytes); erg<sourceBytes.length; )
                        erg+=sourceStream.read(sourceBytes,erg,sourceBytes.length-erg);
    				sourceStream.close();
                    if (!Arrays.equals(sourceBytes, targetBytes)) {
        				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        DiffWriter diffWriter = new GDiffWriter(new DataOutputStream(outputStream));
        				Delta d = new Delta();
        				d.compute(sourceBytes,target.getInputStream(targetEntry),diffWriter);
                        diffWriter.close();

        				ZipEntry outputEntry = new ZipEntry(targetEntry.getName()+".gdiff");
                        outputEntry.setTime(targetEntry.getTime());
        				output.putNextEntry(outputEntry);
        				output.write(outputStream.toByteArray());
                    }
    			}
    		}
            list.close();
            ZipEntry listEntry = new ZipEntry("META-INF/file.list");
            output.putNextEntry(listEntry);
            output.write(listBytes.toByteArray());
			output.closeEntry();	// XDeltaWrapper - do not close
        } finally {
			// XDeltaWrapper start - do not close
			if (false) {
			source.close();
			target.close();
			output.close();
			}// XDeltaWrapper end - do not close
        }
	}

    /**
     * Main method to make {@link #computeDelta(ZipFile, ZipFile, ZipOutputStream)} available at
     * the command line.<br>
     * usage JarDelta source target output
     */
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.err.println("usage JarDelta source target output");
			return;
		}
		// XDeltaWrapper start - do not close
		try (ZipFile source = new ZipFile(args[0]);
			 ZipFile target = new ZipFile(args[1]);
			 ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(Paths.get(args[2])))) {
			new JarDelta().computeDelta(source, target, output);
		}
	}
}
