package dev.hogwai.platform.cli;

import java.io.Console;
import java.io.PrintWriter;

/** Console session backed by {@link System#console()}. */
final class SystemConsoleSession implements ConsoleSession {

    private final Console console;

    SystemConsoleSession() {
        this(System.console());
    }

    SystemConsoleSession(Console console) {
        this.console = console;
    }

    @Override
    public boolean isInteractive() {
        return console != null;
    }

    @Override
    public String readLine(String prompt) {
        if (console == null) {
            return "";
        }
        String value = console.readLine("%s", prompt);
        return value == null ? "" : value;
    }

    @Override
    public void print(String value) {
        if (console != null) {
            try (PrintWriter printWriter = console.writer()) {
                printWriter.print(value);
                printWriter.flush();
            }
        }
    }

    @Override
    public void println(String value) {
        if (console != null) {
            try (PrintWriter printWriter = console.writer()) {
                printWriter.println(value);
                printWriter.flush();
            }
        }
    }
}
