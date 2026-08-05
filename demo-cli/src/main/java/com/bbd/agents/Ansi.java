package com.bbd.agents;

/** Tiny terminal colour helper so the live run reads well from the back row. */
final class Ansi {

    private static final String RESET = "[0m";
    private static final String DIM = "[2m";
    private static final String AMBER = "[38;5;214m"; // the one prompt
    private static final String TEAL = "[38;5;44m";   // the many workers

    private Ansi() {}

    static String dim(String s) { return DIM + s + RESET; }

    static String prompt(String s) { return AMBER + s + RESET; }

    static String worker(String s) { return TEAL + s + RESET; }
}
