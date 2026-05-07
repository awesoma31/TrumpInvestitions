package org.awesoma.trumpinvestitions

import  android.app.Application
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import org.awesoma.trumpinvestitions.data.auth.TokenManager
import org.awesoma.trumpinvestitions.data.network.AppNetwork
import org.awesoma.trumpinvestitions.data.settings.SettingsManager

class TrumpApp : Application() {
    val tokenManager by lazy { TokenManager(this) }
    val settingsManager by lazy { SettingsManager(this) }
    lateinit var openTelemetry: OpenTelemetry
    var network: AppNetwork = AppNetwork("http://placeholder/")

    override fun onCreate() {
        super.onCreate()
        openTelemetry = buildOpenTelemetry()
        network = AppNetwork(settingsManager.baseUrl, tokenManager, openTelemetry)
    }

    fun rebuildNetwork() {
        network = AppNetwork(settingsManager.baseUrl, tokenManager, openTelemetry)
    }

    private fun buildOpenTelemetry(): OpenTelemetry {
        val exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint("${settingsManager.otelEndpoint}/v1/traces")
            .build()
        val resource = Resource.create(
            Attributes.of(
                AttributeKey.stringKey("service.name"), "android-client",
                AttributeKey.stringKey("service.version"), "1.0.0"
            )
        )
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(resource)
            .build()
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
    }
}
