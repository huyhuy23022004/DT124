package com.dt124.extractor;

import com.dt124.model.AttachmentMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated attachment inspection result.
 */
public final class AttachmentAnalysis {

    private final List<AttachmentMeta> attachments = new ArrayList<>();

    public List<AttachmentMeta> getAttachments() {
        return attachments;
    }

    public int getCount() {
        return attachments.size();
    }

    public int getDangerousExtensionCount() {
        int count = 0;
        for (AttachmentMeta meta : attachments) {
            if (meta.isDangerousExtension()) {
                count++;
            }
        }
        return count;
    }

    public int getDoubleExtensionCount() {
        int count = 0;
        for (AttachmentMeta meta : attachments) {
            if (meta.isDoubleExtension()) {
                count++;
            }
        }
        return count;
    }

    public int getMagicMismatchCount() {
        int count = 0;
        for (AttachmentMeta meta : attachments) {
            if (meta.isMagicMismatch()) {
                count++;
            }
        }
        return count;
    }

    public int getPathTraversalCount() {
        int count = 0;
        for (AttachmentMeta meta : attachments) {
            if (meta.isPathTraversal()) {
                count++;
            }
        }
        return count;
    }

    public double getMaxEntropy() {
        double max = 0.0d;
        for (AttachmentMeta meta : attachments) {
            max = Math.max(max, meta.getEntropy());
        }
        return max;
    }
}
