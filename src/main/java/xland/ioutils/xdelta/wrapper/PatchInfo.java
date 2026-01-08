package xland.ioutils.xdelta.wrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PatchInfo {
    private final TreeMap<String, String> adds = new TreeMap<>();
    private final TreeSet<String> removes = new TreeSet<>();
    private final TreeMap<String, String> patches = new TreeMap<>();
    private final TreeMap<String, String> replaces = new TreeMap<>();
//    private final TreeSet<String> softReplaces = new TreeSet<>();

    private static final char C_ADD = '+';
    private static final char C_REM = '-';
    private static final char C_PATCH = '~';
    private static final char C_REPLACE = '!';
//    private static final char C_SOFT_REPLACE_MARKER = '?';

    public void addition(String path, String place) {
        adds.put(path, place);
    }

    public void removal(String path) {
        removes.add(path);
    }

    public void patch(String path, String place) {
        patches.put(path, place);
    }

    public void replacement(String path, String place) {
        replaces.put(path, place);
    }

    public void dumpTo(Appendable out) throws IOException {
        for (Map.Entry<String, String> e : getAdds().entrySet()) {
            out.append(C_ADD);
            writeToken2(e, out);
        }
        for (String s : getRemoves()) {
            out.append(C_REM);
            writeToken1(s, out);
        }
        for (Map.Entry<String, String> e : getPatches().entrySet()) {
            out.append(C_PATCH);
            writeToken2(e, out);
        }
        for (Map.Entry<String, String> e : getReplaces().entrySet()) {
            out.append(C_REPLACE);
            writeToken2(e, out);
        }
    }

    public void dumpTo(OutputStream out) throws IOException {
        dumpTo(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    private static void writeToken1(String token, Appendable out) throws IOException {
        EscapedIO.writeTokens(Collections.singleton(token), out);
    }

    private static void writeToken2(Map.Entry<String, String> entry, Appendable out) throws IOException {
        EscapedIO.writeTokens(Arrays.asList(entry.getKey(), entry.getValue()), out);
    }

    public static PatchInfo readFrom(BufferedReader reader) throws IOException {
        String line;
        PatchInfo instance = new PatchInfo();
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> tokens;
            switch (line.charAt(0)) {
                case C_REM:
                case C_ADD:
                case C_PATCH:
                case C_REPLACE:
                    tokens = EscapedIO.readTokens(line.substring(1));
                    if (tokens.isEmpty() || !(
                            C_REM == line.charAt(0) && tokens.size() == 1 || tokens.size() == 2
                    )) {
                        // token size mismatch, ignore
                        continue;
                    }
                    break;
                default:
                    // unknown line, ignore
                    continue;
            }
            switch (line.charAt(0)) {
                case C_REM:
                    instance.removal(tokens.get(0));
                    break;
                case C_ADD:
                    instance.addition(tokens.get(0), tokens.get(1));
                    break;
                case C_PATCH:
                    instance.patch(tokens.get(0), tokens.get(1));
                    break;
                case C_REPLACE:
                    instance.replacement(tokens.get(0), tokens.get(1));
                    break;
            }
        }
        return instance;
    }

    public NavigableMap<String, String> getAdds() {
        return Collections.unmodifiableNavigableMap(adds);
    }

    public NavigableSet<String> getRemoves() {
        return Collections.unmodifiableNavigableSet(removes);
    }

    public NavigableMap<String, String> getPatches() {
        return Collections.unmodifiableNavigableMap(patches);
    }

    public NavigableMap<String, String> getReplaces() {
        return Collections.unmodifiableNavigableMap(replaces);
    }
}
