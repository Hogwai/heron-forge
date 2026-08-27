package dev.hogwai.platform.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Picocli command group over the stored generations of an application.
 */
@Command(name = "generations", description = "Inspect the stored generations of an application",
        subcommands = {GenerationsListCommand.class, GenerationsMarkCommand.class,
                GenerationsDiffCommand.class})
public final class GenerationsCommand implements Runnable {

    @Spec
    private CommandSpec commandSpec;

    /**
     * Creates the picocli generations command group.
     */
    public GenerationsCommand() {
        // populated by picocli
    }

    @Override
    public void run() {
        commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
    }
}
