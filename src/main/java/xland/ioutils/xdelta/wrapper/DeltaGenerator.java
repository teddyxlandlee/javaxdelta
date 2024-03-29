package xland.ioutils.xdelta.wrapper;

import at.spardat.xma.xdelta.JarDelta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
            JarPatcherMain.log(true, DeltaGenerator::help);
            return;
        }

        Map<Character, String> aliasMap = new HashMap<>(); {
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
        List<Path> files = new ArrayList<>(3);
        final Iterator<Arg> iterator = Arg.parse(args, aliasMap::get).iterator();
        while (iterator.hasNext()) {
            Arg arg = iterator.next();
            if (arg.isRaw()) {
                files.add(Paths.get(arg.getContext()));
            } else {
                switch (arg.getContext()) {
                    case "help":
                        JarPatcherMain.log(true, DeltaGenerator::help);
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
            JarPatcherMain.log(true, DeltaGenerator::help);
            return;
        }
        if (inputName == null)
            JarPatcherMain.log(true, () -> "An input name is recommended");
        if (outputName == null)
            JarPatcherMain.log(true, () -> "An output name is recommended");

        byte[] sha256 = null;
        JarPatcherMain.log(verbose, () -> "Ready");
        try {
            final Path sourceFile = files.get(0);
            if (checksum) {
                sha256 = JarPatcherMain.sha256(sourceFile);
            }
            final File f1 = JarPatcherMain.fileOrTemp(sourceFile, verbose), f2 = JarPatcherMain.fileOrTemp(files.get(1), verbose);

            try (ZipOutputStream os = new ZipOutputStream(Files.newOutputStream(files.get(2)))) {
                ZipEntry z;

                JarPatcherMain.log(verbose, () -> "Generating patch");
                z = new ZipEntry("META-INF/patch.bin");
                os.putNextEntry(z);
                try (ZipFile z1 = new ZipFile(f1); ZipFile z2 = new ZipFile(f2)) {
                    new JarDelta().computeDelta(z1, z2, new ZipOutputStream(wrappedOutputStream(os)));
                }

                JarPatcherMain.log(verbose, () -> "Try writing source file SHA-256");
                tryWrite(os, "META-INF/checksum.bin", sha256);

                JarPatcherMain.log(verbose, () -> "Copying entries");
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
            JarPatcherMain.log(verbose, () -> "Done");
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

    private static OutputStream wrappedOutputStream(OutputStream os) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                os.write(b);
            }

            @Override
            @SuppressWarnings("all")
            public void write(/*@NotNull*/ byte[] b) throws IOException {
                os.write(b);
            }

            @Override
            @SuppressWarnings("all")
            public void write(byte[] b, int off, int len) throws IOException {
                os.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                os.flush();
            }

            // No close
        };
    }
}
