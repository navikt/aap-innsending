package innsending.http

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes

object InnsendingOpenTelemetry {
    val client: OpenTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTraceProvider)
        .setMeterProvider(sdkMeterProvider)
        .setLoggerProvider(sdkLoggerProvider)
        .setPropagators(sdkPropagators)
        .build()
}

private val resource = Resource.getDefault()
    .toBuilder()
    .put(ResourceAttributes.SERVICE_NAME, "innsending")
    .put(ResourceAttributes.SERVICE_NAMESPACE, "aap")
    .build()

private val sdkTraceProvider = SdkTracerProvider.builder()
    .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
    .setResource(resource)
    .build()

private val sdkMeterProvider = SdkMeterProvider.builder()
    .registerMetricReader(PeriodicMetricReader.builder(LoggingMetricExporter.create()).build())
    .setResource(resource)
    .build()

private val sdkLoggerProvider = SdkLoggerProvider.builder()
    .addLogRecordProcessor(BatchLogRecordProcessor.builder(SystemOutLogRecordExporter.create()).build())
    .setResource(resource)
    .build()

private val sdkPropagators = ContextPropagators.create(
    TextMapPropagator.composite(
        W3CTraceContextPropagator.getInstance(),
        W3CBaggagePropagator.getInstance()
    )
)
