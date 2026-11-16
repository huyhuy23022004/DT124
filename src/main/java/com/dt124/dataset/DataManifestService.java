package com.dt124.dataset;

import com.dt124.util.HashUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Produces a data manifest with provenance metadata and SHA-256 checksums for every input file, as
 * required by the data-management rules (section 4 of the requirements).
 */
public final class DataManifestService {

    public static final class DatasetEntry {
        private final String name;
        private final Path directory;
        private final String version;
        private final String license;

        public DatasetEntry(String name, Path directory, String version, String license) {
            this.name = name;
            this.directory = directory;
            this.version = version;
            this.license = license;
        }
    }

    public void writeManifest(Path output, List<DatasetEntry> entries) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("# DT124 Data Manifest");
            writer.newLine();
            writer.write("# generated: " + Instant.now());
            writer.newLine();
            writer.write("# algorithm: SHA-256");
            writer.newLine();
            writer.newLine();

            long grandTotalFiles = 0;
            long grandTotalBytes = 0;
            for (DatasetEntry entry : entries) {
                long files = 0;
                long bytes = 0;
                writer.write("[dataset] " + entry.name);
                writer.newLine();
                writer.write("version=" + entry.version);
                writer.newLine();
                writer.write("license=" + entry.license);
                writer.newLine();
                writer.write("directory=" + entry.directory);
                writer.newLine();
                if (!Files.isDirectory(entry.directory)) {
                    writer.write("status=NOT_PRESENT");
                    writer.newLine();
                    writer.newLine();
                    continue;
                }
                List<Path> filesInDir = new ArrayList<>();
                try (Stream<Path> stream = Files.walk(entry.directory)) {
                    stream.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .forEach(filesInDir::add);
                }
                for (Path file : filesInDir) {
                    String hash = hashFile(file);
                    String relative = entry.directory.relativize(file).toString().replace('\\', '/');
                    writer.write(hash + "  " + relative);
                    writer.newLine();
                    files++;
                    bytes += Files.size(file);
                }
                writer.write("file_count=" + files);
                writer.newLine();
                writer.write("total_bytes=" + bytes);
                writer.newLine();
                writer.newLine();
                grandTotalFiles += files;
                grandTotalBytes += bytes;
            }
            writer.write("[summary] files=" + grandTotalFiles + " bytes=" + grandTotalBytes);
            writer.newLine();
        }
    }

    private String hashFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] data = in.readAllBytes();
            return HashUtil.sha256Hex(data);
        } catch (IOException e) {
            return "ERROR";
        }
    }
}
