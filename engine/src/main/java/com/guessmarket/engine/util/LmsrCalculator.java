package com.guessmarket.engine.util;

import java.util.List;

public class LmsrCalculator {

    /**
     * Calculates the total cost function (the pool of money) using the formula:
     *
     */
    public static double calculateCost(List<Integer> quantities, int bParameter) {
        double sumExp = 0.0;

        //Loop through the option's and add e^(q/b) to the total sum
        for (int q: quantities){
            sumExp += Math.exp((double) q/bParameter);
        }

        return bParameter * Math.log(sumExp);
    }

    /**
     * Calculates the current price of the 'Yes' option using the formula:
     *
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
