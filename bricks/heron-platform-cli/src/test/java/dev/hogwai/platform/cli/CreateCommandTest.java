package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCommandTest {

    @TempDir
    Path tempDirectory;

    @Test
    void printsDirectExamplesWhenNoInteractiveConsoleIsAvailable() {
        RecordingConsole console = new RecordingConsole(false);

        int status = new CommandLine(new CreateCommand(tempDirectory, console)).execute();

        assertThat(status).isEqualTo(2);
        assertThat(console.output()).contains("heron create app <name>")
                .contains("heron create provider <name>")
                .contains("heron create brick <name> --type=data|host");
    }

    @Test
    void interactiveWizardCreatesTheSelectedApplication() throws Exception {
        RecordingConsole console = new RecordingConsole(true, "app", "demo", "com.acme.demo");

        int status = new CommandLine(new CreateCommand(tempDirectory, console)).execute();

        assertThat(status).isZero();
        assertThat(tempDirectory.resolve("demo/src/main/resources/application.yaml")).exists();
        assertThat(Files.readString(tempDirectory.resolve("demo/src/main/resources/application.yaml")))
                .contains("application: demo");
    }

    private static final class RecordingConsole implements ConsoleSession {

        private final boolean interactive;
        private final Deque<String> responses;
        private final StringBuilder output = new StringBuilder();

        private RecordingConsole(boolean interactive, String... responses) {
            this.interactive = interactive;
            this.responses = new ArrayDeque<>();
            this.responses.addAll(java.util.List.of(responses));
        }

        @Override
        public boolean isInteractive() {
            return interactive;
        }

        @Override
        public String readLine(String prompt) {
            output.append(prompt);
            return responses.removeFirst();
        }

        @Override
        public void print(String value) {
            output.append(value);
        }

        @Override
        public void println(String value) {
            output.append(value).append(System.lineSeparator());
        }

        private String output() {
            return output.toString();
        }
    }
}
