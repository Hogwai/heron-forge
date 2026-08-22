package dev.hogwai.platform.cli;

/** Injectable terminal interaction used by interactive CLI commands. */
interface ConsoleSession {

    /** Returns whether input can be read interactively. */
    boolean isInteractive();

    /** Reads one line after displaying the prompt. */
    String readLine(String prompt);

    /** Writes text without a line terminator. */
    void print(String value);

    /** Writes text followed by the platform line terminator. */
    void println(String value);
}
