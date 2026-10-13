package com.guessmarket.engine.util;

import java.util.List;

public class LmsrCalculator {

    /**
     * LMSR cost function (the pool of money): C(q) = b * ln( sum over options of e^(q_i / b) ).
     * A purchase costs C(after) - C(before).
     */
    public static double calculateCost(List<Integer> quantities, int bParameter) {
        double sumExp = 0.0;

        for (int q: quantities){
            sumExp += Math.exp((double) q/bParameter);
        }

        return bParameter * Math.log(sumExp);
    }

    /**
     * Current price of one option: p_i = e^(q_i / b) / sum over options of e^(q_j / b).
     * The prices of all options always add up to 1.
     */
    public static double calculateOptionPrice(int targetQuantity, List<Integer> allQuantites, int bParameter) {
        double expTarget = Math.exp((double) targetQuantity / bParameter);

        double sumExp = 0.0;
        for(int q : allQuantites){
            sumExp += Math.exp((double) q/bParameter);
        }

        return expTarget / sumExp;
    }
}
