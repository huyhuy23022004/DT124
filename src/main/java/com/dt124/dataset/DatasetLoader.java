package com.dt124.dataset;

import com.dt124.extractor.EmailFeatureExtractor;
import com.dt124.extractor.ExtractionResult;
import com.dt124.parser.OfflineEmlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Loads .eml files from a directory tree and runs the full offline extraction pipeline.
 * Supports both ".eml"-suffixed files and extension-less maildir files (Enron style).
 */
public final class DatasetLoader {

    private final OfflineEmlParser parser;
    private final EmailFeatureExtractor extractor;

    public DatasetLoader(OfflineEmlParser parser, EmailFeatureExtractor extractor) {
        this.parser = parser;
        this.extractor = extractor;
    }

    /**
     * Loads up to {@code maxFiles} files (0 = unlimited) recursively.
     *
     * @param acceptAllFiles when true, files without an extension are also accepted (Enron maildir)
     */
    public List<ExtractionResult> load(Path directory, String corpus, int label,
                                       boolean acceptAllFiles, int maxFiles,
                                       LoadStatistics stats) throws IOException {
        List<ExtractionResult> results = new ArrayList<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return results;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isEmlCandidate(path, acceptAllFiles))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(files::add);
        }
        int limit = maxFiles > 0 ? Math.min(maxFiles, files.size()) : files.size();
        for (int i = 0; i < limit; i++) {
            Path path = files.get(i);
            if (stats != null) {
                stats.registerFile(corpus);
            }
            try {
                var parsed = parser.parseFile(path);
                var result = extractor.extract(parsed);
                result.getFeatureRecord().setSourcePath(path.toString());
                result.getFeatureRecord().setSourceCorpus(corpus);
                results.add(result);
                if (parsed.isParsedOk() && stats != null) {
                    stats.registerParsed(corpus);
                } else if (stats != null) {
                    stats.registerFailed();
                }
            } catch (Exception e) {
                if (stats != null) {
                    stats.registerFailed();
                }
            }
        }
        return results;
    }

    private boolean isEmlCandidate(Path path, boolean acceptAllFiles) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".eml") || name.endsWith(".txt") || name.endsWith(".mail")) {
            return true;
        }
        return acceptAllFiles && !name.contains(".");
    }
}
