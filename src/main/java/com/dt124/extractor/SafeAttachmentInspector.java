package com.dt124.extractor;

import com.dt124.model.AttachmentMeta;
import com.dt124.model.RawAttachment;
import com.dt124.util.EntropyCalculator;
import com.dt124.util.HashUtil;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Inspects attachment metadata safely, in RAM only. Implements paper limitation 5: file extension
 * analysis, double-extension detection, magic-bytes vs declared-type mismatch and Shannon entropy.
 * Path-traversal file names are detected as a negative-test signal. Attachments are never written to
 * disk and never executed.
 */
public final class SafeAttachmentInspector {

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "scr", "js", "jse", "vbs", "vbe", "hta", "iso", "img", "lnk", "bat", "cmd",
            "com", "pif", "cpl", "msi", "msp", "jar", "ps1", "psm1", "wsf", "wsh", "dll", "reg",
            "apk", "gadget", "application", "xbap", "msc", "inf", "docm", "xlsm", "pptm", "svg",
            "html", "htm", "chm"
    );

    private static final Set<String> DECOY_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "jpg", "jpeg",
            "png", "gif", "zip", "rar", "xml", "csv", "wav", "mp3", "mp4"
    );

    public AttachmentAnalysis inspect(List<RawAttachment> attachments) {
        AttachmentAnalysis analysis = new AttachmentAnalysis();
        if (attachments == null) {
            return analysis;
        }
        for (RawAttachment raw : attachments) {
            analysis.getAttachments().add(inspectOne(raw));
        }
        return analysis;
    }

    private AttachmentMeta inspectOne(RawAttachment raw) {
        AttachmentMeta meta = new AttachmentMeta();
        String fileName = raw.getFileName() == null ? "" : raw.getFileName();
        meta.setFileName(fileName);
        meta.setContentType(raw.getContentType());
        meta.setDeclaredSize(raw.getDeclaredSize());

        byte[] content = raw.getContent() == null ? new byte[0] : raw.getContent();
        meta.setInspectedBytes(content.length);
        meta.setEntropy(EntropyCalculator.shannonEntropy(content));
        meta.setSha256(HashUtil.sha256Hex(content));

        String normalizedName = fileName.replace('\\', '/');
        meta.setPathTraversal(normalizedName.contains("..")
                || normalizedName.contains("/")
                || normalizedName.matches("(?i)^[a-z]:.*"));

        String baseName = baseName(normalizedName);
        String extension = extensionOf(baseName);
        meta.setExtension(extension);
        meta.setDangerousExtension(DANGEROUS_EXTENSIONS.contains(extension));
        meta.setDoubleExtension(isDoubleExtension(baseName));

        String detected = detectMagic(content);
        meta.setDetectedType(detected);
        meta.setMagicMismatch(isMagicMismatch(extension, detected));
        return meta;
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String extensionOf(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isDoubleExtension(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String[] parts = name.split("\\.");
        if (parts.length < 3) {
            return false;
        }
        String last = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        boolean lastIsDecoyOrDangerous = DECOY_EXTENSIONS.contains(last) || DANGEROUS_EXTENSIONS.contains(last);
        if (!lastIsDecoyOrDangerous) {
            return false;
        }
        for (int i = 1; i < parts.length - 1; i++) {
            String intermediate = parts[i].toLowerCase(Locale.ROOT);
            if (DANGEROUS_EXTENSIONS.contains(intermediate)) {
                return true;
            }
            if (DECOY_EXTENSIONS.contains(intermediate) && DANGEROUS_EXTENSIONS.contains(last)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects coarse file type from magic bytes only. Returns "unknown" when no signature matches.
     */
    public static String detectMagic(byte[] data) {
        if (data == null || data.length < 4) {
            return "unknown";
        }
        if (startsWith(data, 0x4D, 0x5A)) {
            return "pe-executable";
        }
        if (startsWith(data, 0x7F, 0x45, 0x4C, 0x46)) {
            return "elf-executable";
        }
        if (startsWith(data, 0x25, 0x50, 0x44, 0x46)) {
            return "pdf";
        }
        if (startsWith(data, 0x50, 0x4B, 0x03, 0x04) || startsWith(data, 0x50, 0x4B, 0x05, 0x06)) {
            return "zip-archive";
        }
        if (startsWith(data, 0x52, 0x61, 0x72, 0x21)) {
            return "rar-archive";
        }
        if (startsWith(data, 0x37, 0x7A, 0xBC, 0xAF)) {
            return "7z-archive";
        }
        if (startsWith(data, 0x1F, 0x8B)) {
            return "gzip-archive";
        }
        if (startsWith(data, 0xD0, 0xCF, 0x11, 0xE0)) {
            return "ole-document";
        }
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47)) {
            return "png-image";
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return "jpeg-image";
        }
        if (startsWith(data, 0x47, 0x49, 0x46, 0x38)) {
            return "gif-image";
        }
        if (startsWith(data, 0x7B, 0x5C, 0x72, 0x74, 0x66)) {
            return "rtf-document";
        }
        if (startsWithAscii(data, "<!DOCTYPE") || startsWithAscii(data, "<html")
                || startsWithAscii(data, "<HTML") || startsWithAscii(data, "<?xml")
                || startsWithAscii(data, "<script")) {
            return "html-document";
        }
        return "unknown";
    }

    private static boolean isMagicMismatch(String extension, String detected) {
        if (extension == null || extension.isEmpty() || "unknown".equals(detected)) {
            return false;
        }
        return switch (extension) {
            case "pdf" -> !"pdf".equals(detected);
            case "doc", "xls", "ppt", "msi" -> !"ole-document".equals(detected)
                    && !"zip-archive".equals(detected) && !"pe-executable".equals(detected);
            case "docx", "xlsx", "pptx", "odt", "zip", "jar", "apk", "epub" ->
                    !"zip-archive".equals(detected);
            case "rar" -> !"rar-archive".equals(detected);
            case "7z" -> !"7z-archive".equals(detected);
            case "gz", "tgz" -> !"gzip-archive".equals(detected);
            case "png" -> !"png-image".equals(detected);
            case "jpg", "jpeg" -> !"jpeg-image".equals(detected);
            case "gif" -> !"gif-image".equals(detected);
            case "rtf" -> !"rtf-document".equals(detected);
            case "html", "htm" -> !"html-document".equals(detected);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] data, String prefix) {
        if (data.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if ((char) (data[i] & 0xFF) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static Set<String> dangerousExtensions() {
        return DANGEROUS_EXTENSIONS;
    }
}
