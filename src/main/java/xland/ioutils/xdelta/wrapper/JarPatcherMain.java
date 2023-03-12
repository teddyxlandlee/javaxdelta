package xland.ioutils.xdelta.wrapper;

import at.spardat.xma.xdelta.JarPatcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class JarPatcherMain {
    private static String readFromClasspath(String fn) throws IOException {
        try (InputStream s = JarPatcherMain.class.getResourceAsStream(fn)) {
            if (s == null) throw new FileNotFoundException(fn);
            BufferedReader br = new BufferedReader(new InputStreamReader(s));
            StringWriter sw = new StringWriter();
            transferTo(br, sw);
            return sw.toString();
        }
    }

    private static String inputFile() throws IOException {
        return readFromClasspath("/META-INF/input-file");
    }

    private static String outputFile() throws IOException {
        return readFromClasspath("/META-INF/output-file");
    }

    static void log(boolean verbose, Supplier<?> o) { if (verbose) System.err.println(o.get()); }

    static byte[] sha256(Path path) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try (InputStream is = Files.newInputStream(path)) {
            byte[] b = new byte[8192];
            int r;
            while ((r = is.read(b, 0, 8192)) >= 0)
                md.update(b, 0, r);
        }
        return md.digest();
    }

    public static void main(String[] rawArgs) {
        Map<Character, String> aliasMap = new HashMap<>(); {
            aliasMap.put('v', "verbose");
            aliasMap.put('h', "help");
            aliasMap.put('d', "delta");
            aliasMap.put('o', "output");
            aliasMap.put('S', "ignore-checksum");
        }

        boolean verbose = false;
        boolean ignoreChecksum = false;
        String input = null;
        String output = null;
        String delta = null;

        final Iterator<Arg> iterator = Arg.parse(rawArgs, aliasMap::get).iterator();
        while (iterator.hasNext()) {
            Arg arg = iterator.next();
            if (!arg.isRaw()) {
                switch (arg.getContext()) {
                    case "verbose":
                        verbose = true;
                        break;
                    case "delta":
                        delta = iterator.next().toString();
                        break;
                    case "help":
                        log(true, JarPatcherMain::help);
                        return;
                    case "output":
                        output = iterator.next().toString();
                        break;
                    case "ignore-checksum":
                        ignoreChecksum = true;
                        break;
                }
            } else {
                if (input != null) {
                    logAndExit(new IllegalArgumentException("Duplicate files"));
                }
                input = arg.getContext();
            }
        }

        try {
            if (input == null) input = inputFile();
            if (output == null) output = outputFile();
            Path inputFile = Paths.get(input);
            if (!Files.exists(inputFile))
                throw new FileNotFoundException(inputFile.toString());

            if (!ignoreChecksum) {
                try (InputStream inputStream = JarPatcherMain.class.getResourceAsStream("/META-INF/checksum.bin")) {
                    if (inputStream != null) {
                        // read input file
                        final byte[] arr = sha256(inputFile);
                        if (!equal(arr, inputStream)) {
                            StringBuilder sb = new StringBuilder("Checksums are not equal. Required SHA256: ");
                            try {
                                inputStream.close();
                                try (InputStream is2 = JarPatcherMain.class.getResourceAsStream("/META-INF/checksum.bin")) {
                                    if (is2 == null) throw new FileNotFoundException("/META-INF/checksum.bin");
                                    int k;
                                    while ((k = is2.read()) >= 0)
                                        appendHex(k, sb);
                                }
                            } catch (IOException e) {
                                sb.append("???[").append(e).append(']');
                            }

                            sb.append(" Got: ");
                            for (byte b0 : arr) {
                                appendHex(b0, sb);
                            }
                            throw new IOException(sb.toString());
                        }
                    }
                }
            }

            Path outputFile = Paths.get(output);
            File file = fileOrTemp(inputFile, verbose);

            File patch;
            if (delta == null) {
                patch = tempFile();
                log(verbose, () -> "Extracting patch to " + patch);
                final InputStream patchBin = JarPatcherMain.class.getResourceAsStream("/META-INF/patch.bin");
                if (patchBin == null) {
                    throw new FileNotFoundException("/META-INF/patch.bin");
                } else {
                    try (InputStream p = patchBin) {
                        Files.copy(p, patch.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } else {
                patch = fileOrTemp(Paths.get(delta), verbose);
            }

            log(verbose, () -> "Ready");
            new JarPatcher().applyDelta(new ZipFile(file), new ZipFile(patch), new ZipOutputStream(Files.newOutputStream(outputFile)));
            log(verbose, () -> "Done");
        } catch (IOException e) {
            logAndExit(e);
        }
    }

    private static void appendHex(int b0, StringBuilder sb) {
        sb.append("0123456789abcdef".charAt((b0 & 0xf0) >> 4));
        sb.append("0123456789abcdef".charAt(b0 & 0x0f));
    }

    private static boolean equal(byte[] arr, InputStream is) throws IOException {
        int i;
        for (byte b : arr) {
            if ((i = is.read()) < 0) return false;
            if ((b & 0xff) != i) return false;
        }
        return is.read() < 0;
    }

    public static String help() {
        return "Usage: java -jar XDeltaWrapper.jar [-d|--delta path/to/deltaFileOverride] [-h|--help] [-v|--verbose] [path/to/input] [-o|--output path/to/output]";
    }

    static File fileOrTemp(Path path, boolean verbose) throws IOException {
        try {
            return path.toFile();
        } catch (UnsupportedOperationException e) {
            final File file = tempFile();
            log(verbose, () -> "Copying " + path + " into " + file);
            Files.copy(path, file.toPath());
            return file;
        }
    }

    static File tempFile() throws IOException {
        final File tmp = File.createTempFile(UUID.randomUUID().toString(), "tmp");
        tmp.deleteOnExit();
        return tmp;
    }

    static void logAndExit(Throwable t) {
        t.printStackTrace();
        System.exit(-1);
    }

    public static void transferTo(Reader reader, Writer out) throws IOException {
        Objects.requireNonNull(out, "out");
        char[] buffer = new char[8192];
        int nRead;
        while ((nRead = reader.read(buffer, 0, 8192)) >= 0) {
            out.write(buffer, 0, nRead);
        }
    }
}
