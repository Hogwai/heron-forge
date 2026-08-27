package dev.hogwai.platform.cli;

import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Picocli command group for creating Heron projects.
 */
@Command(name = "create", mixinStandardHelpOptions = true,
        description = "Create a Heron application, provider, or brick",
        subcommands = {CreateAppCommand.class, CreateProviderCommand.class, CreateBrickCommand.class})
public final class CreateCommand implements Callable<Integer> {

    private final Path baseDirectory;
    private final ConsoleSession console;

    /**
     * Creates a command group rooted at the current directory.
     */
    public CreateCommand() {
        this(Path.of(""), new SystemConsoleSession());
    }

    /**
     * Creates a command group with injectable directory and console.
     */
    CreateCommand(Path baseDirectory, ConsoleSession console) {
        this.baseDirectory = baseDirectory;
        this.console = console;
    }

    @Override
    public Integer call() {
        if (!console.isInteractive()) {
            printExamples();
            return 2;
        }
        String type = console.readLine("What do you want to create? [app/provider/brick]: ")
                .trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "app" -> createApp();
            case "provider" -> createProvider();
            case "brick" -> createBrick();
            default -> {
                console.println("heron: type must be app, provider, or brick");
                yield 2;
            }
        };
    }

    private int createApp() {
        CreateAppCommand command = new CreateAppCommand(baseDirectory);
        command.name = console.readLine("Project name: ").trim();
        command.packageName = console.readLine("Java package (optional): ").trim();
        return command.call();
    }

    private int createProvider() {
        CreateProviderCommand command = new CreateProviderCommand(baseDirectory);
        command.name = console.readLine("Provider name: ").trim();
        command.packageName = console.readLine("Base package (optional): ").trim();
        command.kind = console.readLine("Capability kind [SOURCE/TRANSFORM] (default SOURCE): ").trim();
        command.language = console.readLine("Language [JAVA/KOTLIN] (default JAVA): ").trim();
        return command.call();
    }

    private int createBrick() {
        CreateBrickCommand command = new CreateBrickCommand(baseDirectory);
        command.name = console.readLine("Brick name: ").trim();
        command.packageName = console.readLine("Java package (optional): ").trim();
        command.type = console.readLine("Brick type [data/host]: ").trim();
        return command.call();
    }

    private void printExamples() {
        console.println("Usage:");
        console.println("  heron create app <name> [--package=<package>]");
        console.println("  heron create provider <name> [--package=<package>] [--kind=SOURCE|TRANSFORM]"
                + " [--language=JAVA|KOTLIN]");
        console.println("  heron create brick <name> --type=data|host");
    }
}
