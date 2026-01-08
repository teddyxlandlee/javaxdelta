/*
 * Copyright (c) 2003, 2007 s IT Solutions AT Spardat GmbH.
 * Copyright (c) 2026 Teddy Li
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
package xland.ioutils.xdelta.wrapper;

import com.nothome.delta.Delta;
import com.nothome.delta.DiffWriter;
import com.nothome.delta.GDiffWriter;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class JarDeltaV2 {

    /**
     * Computes the binary differences of two zip files. For all files contained in source and target which
     * are not equal, the binary difference is caluclated by using
     * {@link com.nothome.delta.Delta#doCompute(SyncPoolOutputStream, SyncPoolOutputStream, DiffWriter)}.
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
	public static void computeDelta(ZipFile source, ZipFile target, ZipOutputStream output) throws IOException {
		final PatchInfo patchInfo = new PatchInfo();
		final NameAllocator rawNamePool = new NameAllocator("raw/", ".bin");
		final NameAllocator patchNamePool = new NameAllocator("patch/", ".bin");

		for (Enumeration<? extends ZipEntry> enumer = source.entries(); enumer.hasMoreElements();) {
			ZipEntry sourceEntry = enumer.nextElement();
			if (target.getEntry(sourceEntry.getName()) == null) {
				patchInfo.removal(sourceEntry.getName());
			}
		}

		ZipEntry nilEntry = null;

		for (Enumeration<? extends ZipEntry> enumer = target.entries(); enumer.hasMoreElements();) {
			ZipEntry targetEntry = enumer.nextElement();
			ZipEntry sourceEntry = source.getEntry(targetEntry.getName());

			if (targetEntry.isDirectory()) {
				if (sourceEntry == null) {
					if (nilEntry == null) {
						nilEntry = new ZipEntry(rawNamePool.nextName());
						nilEntry.setMethod(ZipEntry.STORED);
						nilEntry.setSize(0L);
						output.putNextEntry(nilEntry);
						output.closeEntry();
					}
					patchInfo.addition(targetEntry.getName(), nilEntry.getName());
				}
				continue;
			}

			final SyncPoolOutputStream targetBuf = new SyncPoolOutputStream(BUF_INITIAL_CAPACITY);
			try (InputStream targetStream = target.getInputStream(targetEntry)) {
				JarPatcherMain.transferTo(targetStream, targetBuf);
			}

			if (sourceEntry != null) {
				final SyncPoolOutputStream sourceBuf = new SyncPoolOutputStream(BUF_INITIAL_CAPACITY);
				try (InputStream sourceStream = source.getInputStream(sourceEntry)) {
					JarPatcherMain.transferTo(sourceStream, sourceBuf);
				}

				if (targetBuf.contentEquals(sourceBuf)) {
					// no difference
					continue;
				}

				if (sourceEntry.getSize() <= Delta.DEFAULT_CHUNK_SIZE || targetEntry.getSize() <= Delta.DEFAULT_CHUNK_SIZE) {
					// Do replacement
					final String place = rawNamePool.nextName();
					patchInfo.replacement(targetEntry.getName(), place);
					ZipEntry outputEntry = new ZipEntry(place);
					outputEntry.setTime(targetEntry.getTime());
					output.putNextEntry(outputEntry);
					JarPatcherMain.transferTo(targetBuf.makeInputStream(), output);
				} else {
					// Do GDiff
					final String place = patchNamePool.nextName();
					patchInfo.patch(targetEntry.getName(), place);

					ZipEntry outputEntry = new ZipEntry(place);
					outputEntry.setTime(targetEntry.getTime());
					output.putNextEntry(outputEntry);
					Delta d = new Delta();
					d.doCompute(sourceBuf, targetBuf, new GDiffWriter(output));
				}
			} else {
				// Do addition
				final String place = rawNamePool.nextName();
				patchInfo.addition(targetEntry.getName(), place);
				ZipEntry outputEntry = new ZipEntry(place);
				outputEntry.setTime(targetEntry.getTime());
				output.putNextEntry(outputEntry);
				JarPatcherMain.transferTo(targetBuf.makeInputStream(), output);
			}
		}
		output.putNextEntry(new ZipEntry("META-INF/patch.info"));
		patchInfo.dumpTo(output);
		output.closeEntry();
	}

	static final int BUF_INITIAL_CAPACITY = 1048576;
}
