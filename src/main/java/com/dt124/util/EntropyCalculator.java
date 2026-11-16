package com.dt124.util;

/**
 * Shannon entropy calculator used for domain entropy and attachment byte entropy features.
 */
public final class EntropyCalculator {

    private EntropyCalculator() {
    }

    /**
     * Shannon entropy (base 2, bits per symbol) over a byte array. Returns 0.0 for empty input.
     */
    public static double shannonEntropy(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0d;
        }
        int[] counts = new int[256];
        for (byte b : data) {
            counts[b & 0xFF]++;
        }
        double entropy = 0.0d;
        double length = data.length;
        for (int count : counts) {
            if (count == 0) {
                continue;
            }
            double p = count / length;
            entropy -= p * (Math.log(p) / Math.log(2.0d));
        }
        return entropy;
    }

    /**
     * Shannon entropy over the characters of a string (used for domain/lexical entropy).
     */
    public static double shannonEntropy(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0d;
        }
        int[] counts = new int[Character.MAX_VALUE + 1];
        for (int i = 0; i < text.length(); i++) {
            counts[text.charAt(i)]++;
        }
        double entropy = 0.0d;
        double length = text.length();
        for (int count : counts) {
            if (count == 0) {
                continue;
            }
            double p = count / length;
            entropy -= p * (Math.log(p) / Math.log(2.0d));
        }
        return entropy;
    }
}
