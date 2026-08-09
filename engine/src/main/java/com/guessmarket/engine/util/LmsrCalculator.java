package com.guessmarket.engine.util;

public class LmsrCalculator {

    /**
     * Calculates the total cost function (the pool of money) using the formula:
     * C(q_yes, q_no) = b * ln(e^(q_yes / b) + e^(q_no / b))
     */
    public static double calculateCost(int qYes, int qNo, int bParameter) {
        // cast to double to ensure floating-point division
        double expYes = Math.exp((double) qYes / bParameter);
        double expNo = Math.exp((double) qNo / bParameter);

        return bParameter * Math.log(expYes + expNo);
    }

    /**
     * Calculates the current price of the 'Yes' option using the formula:
     * p_yes = e^(q_yes / b) / (e^(q_yes / b) + e^(q_no / b))
     */
    public static double calculateOptionPrice(int qTarget, int qOther, int bParameter) {
        double expTarget = Math.exp((double) qTarget / bParameter);
        double expOther = Math.exp((double) qOther / bParameter);

        return expTarget / (expTarget + expOther);
    }
}
