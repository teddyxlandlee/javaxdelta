package xland.ioutils.xdelta.wrapper;

import at.spardat.xma.xdelta.JarPatcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class JarPatcherMain {
    private static String inputFile() throws IOException {
        try (InputStream s = JarPatcherMain.class.getResourceAsStream("/META-INF/input-file")) {
            if (s == null) throw new FileNotFoundException("/META-INF/input-file");
            BufferedReader br = new BufferedReader(new InputStreamReader(s));
            StringWriter sw = new StringWriter();
            transferTo(br, sw);
            return sw.toString();
        }
    }

    private static void log(boolean verbose, Supplier<?> o) { if (verbose) System.err.println(o.get()); }

    public static void main(String[] rawArgs) {
        Map<Character, String> aliasMap = new HashMap<>(); {
            aliasMap.put('v', "verbose");
            aliasMap.put('h', "help");
            aliasMap.put('d', "delta");
        }

        boolean verbose = false;
        String input = null;
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
                        System.exit(0);
                }
            } else {
                if (input != null) {
                    logAndExit(new IllegalArgumentException("Duplicate files"));
                }
                input = arg.getContext();
            }
        }

        try {
            File file = tempFile();
            log(verbose, () -> "Created temp file " + file);
            Path output = Paths.get(input == null ? inputFile() : input);
            log(verbose, () -> "Moving " + output + " to " + file);
            Files.move(output, file.toPath(), StandardCopyOption.REPLACE_EXISTING);

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
                patch = new File(delta);
            }

            log(verbose, () -> "Ready");
            new JarPatcher().applyDelta(new ZipFile(file), new ZipFile(patch), new ZipOutputStream(Files.newOutputStream(output)));
        } catch (IOException e) {
            logAndExit(e);
        }
    }

    public static String help() {
        return "Usage: java -jar javaxdelta.jar [-d|--delta path/to/deltaFileOverride] [-h|--help] [-v|--verbose] [path/to/patchedFileOverride]";
    }

    private static File tempFile() throws IOException {
        final File tmp = File.createTempFile(UUID.randomUUID().toString(), "tmp");
        tmp.deleteOnExit();
        return tmp;
    }

    private static void logAndExit(Throwable t) {
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

    private static class Arg {
        //UNIX = 1, RAW = 0, GNU = -1;
        private final int type;
        private final String ctx;

        private Arg(int type, String ctx) {
            this.type = type;
            this.ctx = ctx;
        }

        static Arg unix(char c) { return new Arg(1, String.valueOf(c)); }
        static Arg gnu(String s) { return new Arg(-1, s); }
        static Arg raw(String s) { return new Arg(0, s); }

        String getContext() { return ctx; }
        int getType() { return Integer.signum(type); }
        boolean isGnu() { return type < 0; }
        boolean isUnix() { return type > 0; }
        boolean isRaw() { return type == 0; }

        public String toString() {
            if (isGnu()) return "--" + ctx;
            else if (isRaw()) return ctx;
            return '-' + ctx;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Arg)) return false;
            final Arg other = (Arg) o;
            return Objects.equals(this.ctx, other.ctx) &&
                    this.getType() == other.getType();
        }

        @Override
        public int hashCode() {
            return (Objects.hashCode(this.ctx) << 2) | (getType() & 3);
        }

        static List<Arg> parse(String... args) {
            List<Arg> list = new ArrayList<>();
            for (String s : args) {
                if ("--".equals(s) || s.length() < 2 || s.charAt(0) != '-')
                    list.add(raw(s));
                else if (s.charAt(1) == '-') {
                    int idx;
                    if ((idx = s.indexOf('=')) < 0)
                        list.add(gnu(s.substring(2)));
                    else {
                        list.add(gnu(s.substring(2, idx)));
                        list.add(raw(s.substring(idx + 1)));
                    }
                } else {
                    final char[] chars = s.toCharArray();
                    for (int i = 1; i < chars.length; i++)
                        list.add(unix(chars[i]));
                }
            }
            return list;
        }

        @SuppressWarnings("all")
        static List<Arg> parse(String[] args, Function<Character, String> unixMapper) {
            return parse(args).stream().flatMap(arg -> {
                if (arg.isUnix()) {
                    final String s = unixMapper.apply(arg.getContext().charAt(0));
                    if (s == null) return Stream.empty();
                    return Stream.of(gnu(s));
                }
                return Stream.of(arg);
            }).collect(Collectors.toList());
        }
    }
}
