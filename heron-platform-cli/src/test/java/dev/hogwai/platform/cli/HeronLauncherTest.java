package dev.hogwai.platform.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.host.api.HostAdapter;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.HostConfiguration;
import dev.hogwai.platform.host.api.HostException;
import dev.hogwai.platform.host.api.InvocationRequest;
import dev.hogwai.platform.host.api.InvocationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.ParameterException;

class HeronLauncherTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesTheTwoSupportedCommandForms() throws IOException {
        Path configuration = configurationFile();

        assertThat(StartCommand.parse(new String[] {"start", "--config", configuration.toString()}))
                .satisfies(command -> {
                    assertThat(command.configuration()).isEqualTo(configuration);
                    assertThat(command.port()).isEqualTo(8080);
                });
        assertThat(StartCommand.parse(new String[] {"start", "--config", configuration.toString(), "--port", "0"}))
                .satisfies(command -> assertThat(command.port()).isZero());
    }

    @Test
    void rejectsMalformedCommandsAndConfigurationFiles() throws IOException {
        Path configuration = configurationFile();
        assertRejected();
        assertRejected("run", "--config", configuration.toString());
        assertRejected("start", "--config");
        assertRejected("start", "--config", configuration.toString(), "--unknown", "value");
        assertRejected("start", "--config", temporaryDirectory.resolve("missing.yaml").toString());
        assertRejected("start", "--config", temporaryDirectory.toString());
        assertRejected("start", "--config", configuration.toString(), "--port");
        assertRejected("start", "--config", configuration.toString(), "--port", "-1");
        assertRejected("start", "--config", configuration.toString(), "--port", "65536");
        assertRejected("start", "--config", configuration.toString(), "--port", "not-a-port");
    }

    @Test
    void usesPortZeroInHostConfiguration() throws IOException {
        Path configuration = configurationFile();
        RecordingAdapter adapter = new RecordingAdapter();
        RecordingApplication application = new RecordingApplication(adapter.events);
        StartCommand command = StartCommand.parse(
                new String[] {"start", "--config", configuration.toString(), "--port", "0"});

        int status = HeronLauncher.run(command,
                ignored -> application, () -> adapter, () -> { });

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
        StartCommand command = StartCommand.parse(new String[] {"start", "--config", configuration.toString()});

        int status = HeronLauncher.run(command,
                ignored -> application, () -> adapter, () -> { });

        assertThat(status).isNotZero();
        assertThat(application.closeCount).isEqualTo(1);
        assertThat(events).containsSubsequence("host.start", "host.stop", "host.close", "application.close");
    }

    @Test
    void stopsHostBeforeClosingApplication() throws IOException {
        Path configuration = configurationFile();
        List<String> events = new ArrayList<>();
        RecordingApplication application = new RecordingApplication(events);
        RecordingAdapter adapter = new RecordingAdapter(events);
        StartCommand command = StartCommand.parse(new String[] {"start", "--config", configuration.toString()});

        int status = HeronLauncher.run(command,
                ignored -> application, () -> adapter, () -> { });

        assertThat(status).isZero();
        assertThat(events).containsSubsequence("host.start", "host.stop", "host.close", "application.close");
        assertThat(application.closeCount).isEqualTo(1);
        assertThat(adapter.stopCount).isEqualTo(1);
    }

    @Test
    void pinsPicocliInTheCliOnlyAndHasNoFactoryDemoMain() throws IOException {
        assertThat(Files.readString(Path.of("build.gradle.kts")))
                .contains("info.picocli:picocli:4.7.7");
        Path demoSources = Path.of("../examples/factory-demo/src");
        if (Files.exists(demoSources)) {
            try (var paths = Files.walk(demoSources)) {
                assertThat(paths.filter(path -> path.toString().endsWith(".java"))
                        .map(this::readSource)
                        .toList())
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
        public List<dev.hogwai.platform.host.api.EntrypointDescriptor> entrypoints() {
            return List.of();
        }

        @Override
        public InvocationResult invoke(InvocationRequest request) {
            return null;
        }

        @Override
        public void close() {
            if (closeCount++ == 0) {
                events.add("application.close");
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
            events.add("host.start");
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
            events.add("host.stop");
        }

        @Override
        public void close() {
            events.add("host.close");
        }
    }
}
