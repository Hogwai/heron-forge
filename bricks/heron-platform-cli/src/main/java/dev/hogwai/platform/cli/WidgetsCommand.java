package dev.hogwai.platform.cli;

import picocli.CommandLine.Command;

/** Container for widget-surface subcommands. */
@Command(name = "widgets", description = "Widget surface tooling",
        subcommands = {WidgetsExportCommand.class})
public final class WidgetsCommand {

    /** Creates the picocli widgets container command. */
    public WidgetsCommand() {
        // populated by picocli
    }
}
