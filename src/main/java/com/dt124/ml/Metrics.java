package com.dt124.ml;

import weka.classifiers.Evaluation;

import java.util.Locale;

/**
 * Immutable bundle of the classification metrics required by the project:
 * Precision, Recall, F1 (phishing), Macro-F1, ROC-AUC, PR-AUC, FPR and Accuracy,
 * plus the confusion-matrix counts (TP/TN/FP/FN), Specificity and MCC used for the
 * security-oriented evaluation table.
 */
public final class Metrics {

    private final double precision;
    private final double recall;
    private final double f1;
    private final double macroF1;
    private final double rocAuc;
    private final double prAuc;
    private final double fpr;
    private final double accuracy;
    private final double tp;
    private final double tn;
    private final double fp;
    private final double fn;
    private final double specificity;
    private final double mcc;

    public Metrics(double precision, double recall, double f1, double macroF1,
                   double rocAuc, double prAuc, double fpr, double accuracy) {
        this(precision, recall, f1, macroF1, rocAuc, prAuc, fpr, accuracy, 0, 0, 0, 0, 0, 0);
    }

    public Metrics(double precision, double recall, double f1, double macroF1,
                   double rocAuc, double prAuc, double fpr, double accuracy,
                   double tp, double tn, double fp, double fn, double specificity, double mcc) {
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.macroF1 = macroF1;
        this.rocAuc = rocAuc;
        this.prAuc = prAuc;
        this.fpr = fpr;
        this.accuracy = accuracy;
        this.tp = tp;
        this.tn = tn;
        this.fp = fp;
        this.fn = fn;
        this.specificity = specificity;
        this.mcc = mcc;
    }

    public static Metrics empty() {
        return new Metrics(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static Metrics fromEvaluation(Evaluation eval, int positiveIndex) throws Exception {
        int negativeIndex = positiveIndex == 0 ? 1 : 0;
        double precision = eval.precision(positiveIndex);
        double recall = eval.recall(positiveIndex);
        double f1 = eval.fMeasure(positiveIndex);
        double macroF1 = (eval.fMeasure(positiveIndex) + eval.fMeasure(negativeIndex)) / 2.0d;
        double rocAuc = safe(() -> eval.areaUnderROC(positiveIndex));
        double prAuc = safe(() -> eval.areaUnderPRC(positiveIndex));
        double fpr = eval.falsePositiveRate(positiveIndex);
        double accuracy = eval.pctCorrect() / 100.0d;
        double tp = safe(() -> eval.numTruePositives(positiveIndex));
        double tn = safe(() -> eval.numTrueNegatives(positiveIndex));
        double fp = safe(() -> eval.numFalsePositives(positiveIndex));
        double fn = safe(() -> eval.numFalseNegatives(positiveIndex));
        double specificity = safe(() -> eval.trueNegativeRate(positiveIndex));
        double mcc = safe(() -> eval.matthewsCorrelationCoefficient(positiveIndex));
        return new Metrics(precision, recall, f1, macroF1, rocAuc, prAuc, fpr, accuracy,
                tp, tn, fp, fn, specificity, mcc);
    }

    private static double safe(CheckedSupplier supplier) {
        try {
            double value = supplier.get();
            return Double.isNaN(value) ? 0.0d : value;
        } catch (Exception e) {
            return 0.0d;
        }
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getF1() {
        return f1;
    }

    public double getMacroF1() {
        return macroF1;
    }

    public double getRocAuc() {
        return rocAuc;
    }

    public double getPrAuc() {
        return prAuc;
    }

    public double getFpr() {
        return fpr;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public double getTruePositives() {
        return tp;
    }

    public double getTrueNegatives() {
        return tn;
    }

    public double getFalsePositives() {
        return fp;
    }

    public double getFalseNegatives() {
        return fn;
    }

    public double getSpecificity() {
        return specificity;
    }

    public double getMcc() {
        return mcc;
    }

    public String formatted() {
        return String.format(Locale.ROOT,
                "P=%.3f R=%.3f F1=%.3f MacroF1=%.3f ROC=%.3f PR=%.3f FPR=%.3f Acc=%.3f Spec=%.3f MCC=%.3f",
                precision, recall, f1, macroF1, rocAuc, prAuc, fpr, accuracy, specificity, mcc);
    }

    /**
     * Human-readable confusion matrix, positive class = phishing.
     */
    public String confusionFormatted() {
        return String.format(Locale.ROOT, "TP=%.0f TN=%.0f FP=%.0f FN=%.0f Spec=%.3f MCC=%.3f",
                tp, tn, fp, fn, specificity, mcc);
    }

    @FunctionalInterface
    private interface CheckedSupplier {
        double get() throws Exception;
    }
}
