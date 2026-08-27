package dev.hogwai.platform.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.HostAdapter;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.HostConfiguration;
import dev.hogwai.platform.spi.host.HostException;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.host.StructuredPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.ParameterException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeronLauncherTest {

    public static final String HOST_STOP = "host.stop";
    public static final String START = "start";
    public static final String CONFIG = "--config";
    public static final String HOST_CLOSE = "host.close";
    public static final String APPLICATION_CLOSE = "application.close";
    public static final String HOST_START = "host.start";
    public static final String PORT = "--port";
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesTheTwoSupportedCommandForms() throws IOException {
        Path configuration = configurationFile();

        assertThat(StartCommand.parse(new String[]{START, CONFIG, configuration.toString()}))
                .satisfies(command -> {
                    assertThat(command.configuration()).isEqualTo(configuration);
                    assertThat(command.port()).isEqualTo(8080);
                });
        assertThat(StartCommand.parse(new String[]{START, CONFIG, configuration.toString(), PORT, "0"}))
                .satisfies(command -> assertThat(command.port()).isZero());
    }

    @Test
    void rejectsMalformedCommandsAndConfigurationFiles() throws IOException {
        Path configuration = configurationFile();
        assertRejected();
        assertRejected("run", CONFIG, configuration.toString());
        assertRejected(START, CONFIG);
        assertRejected(START, CONFIG, configuration.toString(), "--unknown", "value");
        assertRejected(START, CONFIG, temporaryDirectory.resolve("missing.yaml").toString());
        assertRejected(START, CONFIG, temporaryDirectory.toString());
        assertRejected(START, CONFIG, configuration.toString(), PORT);
        assertRejected(START, CONFIG, configuration.toString(), PORT, "-1");
        assertRejected(START, CONFIG, configuration.toString(), PORT, "65536");
        assertRejected(START, CONFIG, configuration.toString(), PORT, "not-a-port");
    }

    @Test
    void usesPortZeroInHostConfiguration() throws IOException {
        Path configuration = configurationFile();
        RecordingAdapter adapter = new RecordingAdapter();
        RecordingApplication application = new RecordingApplication(adapter.events);
        StartCommand command = StartCommand.parse(
                new String[]{START, CONFIG, configuration.toString(), PORT, "0"});

        int status = HeronLauncher.run(command,
                ignored -> application, () -> adapter, () -> {
                });

        assertThat(status).isZero();
        assertThat(adapter.configuration.port()).isZero();
    }

    @Test
    void closesApplicationWhenHostStartupFails() throws IOException {
        Path configuration = configurationFile();
        List<String> events = new ArrayList<>();
        RecordingApplication application = new RecordingApplication(events);
        RecordingAdapter adapter = new RecordingAdapter(events);
        adapter.failOnStart = true;
        StartCommand command = StartCommand.parse(new String[]{START, CONFIG, configuration.toString()});

        int status = HeronLauncher.run(command,
                ignored -> application, () -> adapter, () -> {
                });

        assertThat(status).isNotZero();
        assertThat(application.closeCount).isEqualTo(1);
        assertThat(events).containsSubsequence(HOST_START, HOST_STOP, HOST_CLOSE, APPLICATION_CLOSE);
    }

    @Test
    void stopsHostBeforeClosingApplication() throws IOException {
        Path configuration = configurationFile();
        List<String> events = new ArrayList<>();
        try (RecordingApplication application = new RecordingApplication(events);
             RecordingAdapter adapter = new RecordingAdapter(events)) {
            StartCommand command = StartCommand.parse(new String[]{START, CONFIG, configuration.toString()});

            int status = HeronLauncher.run(command,
                    ignored -> application, () -> adapter, () -> {
                    });

            assertThat(status).isZero();
            assertThat(events).containsSubsequence(HOST_START, HOST_STOP, HOST_CLOSE, APPLICATION_CLOSE);
            assertThat(application.closeCount).isEqualTo(1);
            assertThat(adapter.stopCount).isEqualTo(1);
        }
    }

    @Test
    void printsUsageInsteadOfThrowingWhenNoCommandIsSupplied() {
        assertThat(HeronLauncher.run(new String[]{})).isZero();
        assertThatThrownBy(() -> StartCommand.parse(new String[]{}))
                .isInstanceOf(ParameterException.class);
    }

    @Test
    void factoryDemoHasNoMainMethod() throws IOException {
        Path demoSources = Path.of("../examples/factory-demo/src");
        if (Files.exists(demoSources)) {
            try (var paths = Files.walk(demoSources)) {
                assertThat(paths.filter(path -> path.toString().endsWith(".java"))
                        .map(this::readSource)
                        .toList())
                        .isNotEmpty()
                        .allSatisfy(source -> assertThat(source).doesNotContain("static void main("));
            }
        }
    }

    private Path configurationFile() throws IOException {
        Path file = temporaryDirectory.resolve("factory.yaml");
        Files.writeString(file, "apiVersion: platform.dev/v1alpha1\nkind: Application\n");
        return file;
    }

    private void assertRejected(String... arguments) {
        assertThatThrownBy(() -> StartCommand.parse(arguments))
                .isInstanceOf(ParameterException.class)
                .hasMessageNotContaining("Exception")
                .hasMessageNotContaining("at ");
    }

    private String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source", exception);
        }
    }

    private static final class RecordingApplication implements HostApplication {
        private final List<String> events;
        private int closeCount;

        private RecordingApplication(List<String> events) {
            this.events = events;
        }

        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return List.of();
        }

        @Override
        public ExecutionOutcome execute(InvocationRequest request) {
            return ExecutionOutcome.materialized(new StructuredPayload(Map.of()));
        }

        @Override
        public void close() {
            if (closeCount++ == 0) {
                events.add(APPLICATION_CLOSE);
            }
        }
    }

    private static final class RecordingAdapter implements HostAdapter {
        private final List<String> events;
        private HostConfiguration configuration;
        private int stopCount;
        private boolean failOnStart;

        private RecordingAdapter() {
            this(new ArrayList<>());
        }

        private RecordingAdapter(List<String> events) {
            this.events = events;
        }

        @Override
        public void start(HostApplication application, HostConfiguration configuration) throws HostException {
            this.configuration = configuration;
            events.add(HOST_START);
            if (failOnStart) {
                throw new HostException("startup failed");
            }
        }

        @Override
        public boolean ready() {
            return !failOnStart;
        }

        @Override
        public void stop() {
            stopCount++;
            events.add(HOST_STOP);
        }

        @Override
        public void close() {
            events.add(HOST_CLOSE);
        }
    }
}
