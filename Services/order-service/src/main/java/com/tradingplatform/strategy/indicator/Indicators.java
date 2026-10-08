package com.tradingplatform.strategy.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Moving statistics over the newest prices of a series (oldest first) */
public final class Indicators {

    private Indicators() {
    }

    /** Simple moving average of the last n prices */
    public static double sma(List<BigDecimal> prices, int n) {
        return lastN(prices, n).stream().mapToDouble(BigDecimal::doubleValue).average().orElse(Double.NaN);
    }

    /** Population standard deviation of the last n prices */
    public static double stdDev(List<BigDecimal> prices, int n) {
        List<BigDecimal> window = lastN(prices, n);
        double mean = window.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(Double.NaN);
        double variance = window.stream()
            .mapToDouble(p -> Math.pow(p.doubleValue() - mean, 2))
            .average()
            .orElse(Double.NaN);
        return Math.sqrt(variance);
    }

    /** A value rounded for display and storage (6 decimals) */
    public static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** "1,234.56" style text for reasons */
    static String money(double value) {
        return String.format("%,.2f", value);
    }

    private static List<BigDecimal> lastN(List<BigDecimal> prices, int n) {
        if (n <= 0 || prices.size() < n) {
            throw new IllegalArgumentException("Need " + n + " prices, have " + prices.size());
        }
        return prices.subList(prices.size() - n, prices.size());
    }
}
