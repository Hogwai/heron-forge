package dev.hogwai.platform.spi.host;

/** Result of a synchronous host application invocation. */
public sealed interface InvocationResult permits InvocationSuccess, InvocationFailure {
}
