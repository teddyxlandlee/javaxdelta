/*
 * Copyright (c) 2023, 2026 Teddy Li
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DeltaGenerator {
    // DeltaGenerator [-J/--wrapper path/to/wrapper.jar] [-h/--help] [-v/--verbose] [-S/--no-checksum] [-i/--input name.jar] [-o/--output name.jar] a.jar b.jar output.jar
    public static void main(String[] args) {
        if (args.length == 0) {
            JarPatcherMain.log(true, help());
            return;
        }

        Map<Character, String> aliasMap = new HashMap<>(8); {
            aliasMap.put('v', "verbose");
            aliasMap.put('h', "help");
            aliasMap.put('J', "wrapper");
            aliasMap.put('i', "input");
            aliasMap.put('o', "output");
            aliasMap.put('S', "no-checksum");
        }

        boolean verbose = false;
        boolean checksum = true;
        String wrapper = null;
        String inputName = null, outputName = null;
        ArrayList<Path> files = new ArrayList<>(3);
        final Iterator<Arg> iterator = Arg.parse(args, aliasMap::get).iterator();
        while (iterator.hasNext()) {
            Arg arg = iterator.next();
            if (arg.isRaw()) {
                files.add(Paths.get(arg.getContext()));
            } else {
                switch (arg.getContext()) {
                    case "help":
                        JarPatcherMain.log(true, help());
                        return;
                    case "verbose":
                        verbose = true;
                        break;
                    case "wrapper":
                        wrapper = iterator.next().toString();
                        break;
                    case "input":
                        inputName = iterator.next().toString();
                        break;
                    case "output":
                        outputName = iterator.next().toString();
                        break;
                    case "no-checksum":
                        checksum = false;
                        break;
                }
            }
        }
        if (files.size() != 3) {
            JarPatcherMain.log(true, help());
            return;
        }
        if (inputName == null)
            JarPatcherMain.log(true, "An input name is recommended");
        if (outputName == null)
            JarPatcherMain.log(true, "An output name is recommended");

        byte[] sha256 = null;
        JarPatcherMain.log(verbose, "Ready");
        try {
            final Path sourceFile = files.get(0);
            if (checksum) {
                sha256 = JarPatcherMain.sha256(sourceFile);
            }
            final File f1 = JarPatcherMain.fileOrTemp(sourceFile, verbose), f2 = JarPatcherMain.fileOrTemp(files.get(1), verbose);

            try (ZipOutputStream os = new ZipOutputStream(Files.newOutputStream(files.get(2)))) {
                ZipEntry z;

                JarPatcherMain.log(verbose, "Generating patch");
                z = new ZipEntry("META-INF/patch.bin");
                os.putNextEntry(z);
                try (ZipFile z1 = new ZipFile(f1);
                     ZipFile z2 = new ZipFile(f2);
                     ZipOutputStream zos = new ZipOutputStream(wrappedOutputStream(os))) {
                    JarDeltaV2.computeDelta(z1, z2, zos);
                } finally {
                    os.closeEntry();
                }

                JarPatcherMain.log(verbose, "Try writing source file SHA-256");
                tryWrite(os, "META-INF/checksum.bin", sha256);

                JarPatcherMain.log(verbose, "Copying entries");
                tryWrite(os, "META-INF/input-file", inputName);
                tryWrite(os, "META-INF/output-file", outputName);

                try (ZipInputStream is = new ZipInputStream(getZis(wrapper))) {
                    while (true) {
                        z = is.getNextEntry();
                        if (z == null) break;
                        os.putNextEntry(z);

                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = is.read(buf, 0, 8192)) >= 0) {
                            os.write(buf, 0, read);
                        }
                    }
                }
            }
            JarPatcherMain.log(verbose, "Done");
        } catch (IOException e) {
            JarPatcherMain.logAndExit(e);
        }
    }

    private static void tryWrite(ZipOutputStream os, String fn, /*@Nullable*/ String value) throws IOException {
        if (value == null) return;
        tryWrite(os, fn, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void tryWrite(ZipOutputStream os, String fn, /*@Nullable*/ byte[] value) throws IOException {
        if (value == null) return;
        ZipEntry e = new ZipEntry(fn);
        os.putNextEntry(e);
        os.write(value);
        os.closeEntry();
    }

    private static InputStream getZis(String k) throws IOException {
        if (k == null)
            return DeltaGenerator.class.getResourceAsStream("/META-INF/wrapper.jar");
        return Files.newInputStream(Paths.get(k));
    }

    public static String help() {
        return "Usage: java -cp XDeltaWrapper.jar " + DeltaGenerator.class.getName() +
                " [-J/--wrapper path/to/wrapper.jar] [-h/--help] [-v/--verbose] [-S/--no-checksum] [-i/--input name.jar] [-o/--output name.jar] a.jar b.jar output.jar";
    }

    private static FilterOutputStream wrappedOutputStream(OutputStream os) {
        return new FilterOutputStream(os) {
            @Override
            public void close() throws IOException {
                super.flush();
            }
        };
    }
}
