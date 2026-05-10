package com.coreissuer.common.util;

import java.util.Random;

public final class LuhnUtils {

    private static final Random RANDOM = new Random();

    private LuhnUtils() {
        // utility class
    }

    /**
     * Generates a random 16-digit PAN starting with a predefined BIN.
     */
    public static String generatePan(String bin) {
        if (bin == null || bin.length() >= 15) {
            throw new IllegalArgumentException("Invalid BIN");
        }
        StringBuilder panBuilder = new StringBuilder(bin);
        while (panBuilder.length() < 15) {
            panBuilder.append(RANDOM.nextInt(10));
        }
        
        String partialPan = panBuilder.toString();
        int checkDigit = calculateCheckDigit(partialPan);
        return partialPan + checkDigit;
    }

    /**
     * Calculates the Luhn check digit for a given partial PAN.
     */
    public static int calculateCheckDigit(String partialPan) {
        int sum = 0;
        boolean alternate = true;
        
        for (int i = partialPan.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(partialPan.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        
        return (sum * 9) % 10;
    }

    /**
     * Validates a PAN using the Luhn algorithm.
     */
    public static boolean isValid(String pan) {
        int sum = 0;
        boolean alternate = false;
        
        for (int i = pan.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(pan.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }
}
