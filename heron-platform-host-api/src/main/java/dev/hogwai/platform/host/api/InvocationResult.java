package dev.hogwai.platform.host.api;

/** Result of a synchronous host application invocation. */
public sealed interface InvocationResult permits InvocationSuccess, InvocationFailure {
}
