package xland.ioutils.xdelta.wrapper;

import com.nothome.delta.GDiffPatcher;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JarPatcherV2 {
    public static void applyDeltaCompatible(Path source, File patch, Path output, boolean ignoresMismatch) throws IOException {
        File legacySourceFile = null;
        try (ZipInputStream sourceStream = new ZipInputStream(Files.newInputStream(source));
             ZipFile patchFile = new ZipFile(patch);
             ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(output))) {
            applyDelta(sourceStream, patchFile, outputStream, ignoresMismatch);
        } catch (FileNotFoundException e) {
            if (e.getClass() != FileNotFoundException.class || !"META-INF/patch.info".equals(e.getMessage())) {
                throw e;
            }
            // Try legacy
            legacySourceFile = JarPatcherMain.fileOrTemp(source);
        }
        if (legacySourceFile != null) {
            // Try legacy
            try (ZipFile sourceFile = new ZipFile(legacySourceFile);
                 ZipFile patchFile = new ZipFile(patch);
                 ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(output))) {
                if (patchFile.getEntry("META-INF/file.list") != null) {
                    //noinspection deprecation
                    new at.spardat.xma.xdelta.JarPatcher().applyDelta(sourceFile, patchFile, outputStream);
                } else {
                    throw new FileNotFoundException("META-INF/patch.info");
                }
            }
        }
    }

    public static void applyDelta(ZipInputStream source, ZipFile patch, ZipOutputStream output, boolean ignoresMismatch) throws IOException {
        ZipEntry patchInfoEntry = patch.getEntry("META-INF/patch.info");
        if (patchInfoEntry == null) {
            throw new FileNotFoundException("META-INF/patch.info");
        }
        PatchInfo patchInfo;
        try (BufferedReader patchInfoReader = new BufferedReader(new InputStreamReader(patch.getInputStream(patchInfoEntry)))) {
            patchInfo = PatchInfo.readFrom(patchInfoReader);
        }

        HashSet<String> toRemove = new LinkedHashSet<>(patchInfo.getRemoves());
        HashMap<String, String> toReplace = new LinkedHashMap<>(patchInfo.getReplaces());
        HashMap<String, String> toPatch = new LinkedHashMap<>(patchInfo.getPatches());

        ZipEntry sourceEntry;
        while ((sourceEntry = source.getNextEntry()) != null) {
            String sourceEntryName = sourceEntry.getName();

            if (patchInfo.getAdds().containsKey(sourceEntryName)) {
                if (!ignoresMismatch) {
                    throw new FileAlreadyExistsException(sourceEntryName + " (for addition)");
                } else {
                    continue;   // already added, skip original
                }
            }

            if (toRemove.remove(sourceEntryName)) continue; // removal

            if (sourceEntry.isDirectory()) {
                // Directories cannot be patched/replaced; they are equivalent to as-is copy
                output.putNextEntry(new ZipEntry(sourceEntry));
                continue;
            }

            String place;

            ZipEntry outputEntry = new ZipEntry(sourceEntryName);

            place = toReplace.remove(sourceEntryName);
            if (place != null) {
                ZipEntry replaceEntry = patch.getEntry(place);
                if (replaceEntry == null) {
                    throw new FileNotFoundException(sourceEntryName + " (for replacement)");
                }

                outputEntry.setTime(replaceEntry.getTime());
                output.putNextEntry(outputEntry);
                try (InputStream in = patch.getInputStream(replaceEntry)) {
                    JarPatcherMain.transferTo(in, output);
                }
                continue;
            }

            place = toPatch.remove(sourceEntryName);
            if (place != null) {
                ZipEntry patchEntry = patch.getEntry(place);
                if (patchEntry == null) {
                    throw new FileNotFoundException(sourceEntryName + " (for patch)");
                }

                outputEntry.setTime(patchEntry.getTime());
                output.putNextEntry(outputEntry);

                try (InputStream patchStream = patch.getInputStream(patchEntry)) {
                    // Do GDiff patch
                    GDiffPatcher diffPatcher = new GDiffPatcher();

                    try (SyncPoolOutputStream sourceBuf = new SyncPoolOutputStream(JarDeltaV2.BUF_INITIAL_CAPACITY)) {
                        // Transfer source to buffer
                        JarPatcherMain.transferTo(source, sourceBuf);
                        diffPatcher.patch(sourceBuf.makeSeekableSource(), patchStream, output);
                    }
                }
                continue;
            }

            // Copy original entry
            outputEntry = new ZipEntry(sourceEntry);
            output.putNextEntry(outputEntry);
            JarPatcherMain.transferTo(source, output);
        }

        // Lastly add all additions
        for (Map.Entry<String, String> e : patchInfo.getAdds().entrySet()) {
            ZipEntry targetEntry = new ZipEntry(e.getKey());
            sourceEntry = patch.getEntry(e.getValue());
            if (sourceEntry == null) {
                throw new FileNotFoundException(e.getKey() + " (for addition)");
            }

            targetEntry.setTime(sourceEntry.getTime());
            output.putNextEntry(targetEntry);

            try (InputStream in = patch.getInputStream(sourceEntry)) {
                JarPatcherMain.transferTo(in, output);
            }
        }

        if (toRemove.isEmpty() && toReplace.isEmpty() && toPatch.isEmpty()) return;

        final ArrayList<String> errors = new ArrayList<>(3);
        if (!toRemove.isEmpty()) errors.add(collectNotFound("toRemove: ", toRemove));
        if (!toReplace.isEmpty()) errors.add(collectNotFound("toReplace: ", toReplace.keySet()));
        if (!toPatch.isEmpty()) errors.add(collectNotFound("toPatch: ", toPatch.keySet()));

        final String errorMessage = String.join("; ", errors);
        throw new FileNotFoundException(errorMessage);
    }

    private static String collectNotFound(String header, Iterable<? extends String> filenames) {
        StringJoiner joiner = new StringJoiner(", ", header, "");
        filenames.forEach(joiner::add);
        return joiner.toString();
    }
}
