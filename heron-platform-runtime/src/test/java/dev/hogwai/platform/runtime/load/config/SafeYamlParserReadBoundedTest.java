package dev.hogwai.platform.runtime.load.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.load.config.yaml.YamlLimits;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SafeYamlParserReadBoundedTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();

    private static final String VALID = """
        apiVersion: platform.dev/v1alpha1
        kind: Application
        metadata:
          name: x
        spec:
          capabilities: []
        """;

    @Test
    void maxBytesIntegerMaxRequestsSafely() {
        RecordingInputStream in = new RecordingInputStream(VALID.getBytes(StandardCharsets.UTF_8));
        YamlLimits limits = new YamlLimits(Integer.MAX_VALUE, 20, 100, 100);

        ParsedApplication result = PARSER.parse(in, limits);

        assertThat(result.isValid()).isTrue();
        assertThat(in.negativeRequested).isFalse();
        assertThat(in.totalRequested).isLessThanOrEqualTo((long) Integer.MAX_VALUE + 1);
    }

    @Test
    void doesNotRequestBeyondMaxBytesPlusOne() {
        String doc = "kind: " + "x".repeat(1000) + "\n";
        RecordingInputStream in = new RecordingInputStream(doc.getBytes(StandardCharsets.UTF_8));
        YamlLimits limits = new YamlLimits(64, 20, 100, 100);

        ParsedApplication result = PARSER.parse(in, limits);

        assertThat(result.isValid()).isFalse();
        assertThat(in.totalRequested).isLessThanOrEqualTo(65L);
        assertThat(in.negativeRequested).isFalse();
    }

    @Test
    void smallDocParsesWithDefaultLimits() {
        ParsedApplication result = PARSER.parse(new ByteArrayInputStream(VALID.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.isValid()).isTrue();
    }

    /**
     * Controlled input stream that records requested read lengths.
     */
    private static final class RecordingInputStream extends InputStream {

        private final byte[] data;
        private int pos;
        private long totalRequested;
        private boolean negativeRequested;

        private RecordingInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            if (pos >= data.length) {
                return -1;
            }
            return data[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len < 0) {
                negativeRequested = true;
            }
            totalRequested += len;
            if (pos >= data.length) {
                return -1;
            }
            int n = Math.min(len, data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }
}
