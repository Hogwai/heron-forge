package dev.hogwai.platform.examples.kotlinprovider

import dev.hogwai.platform.spi.CapabilityKind
import dev.hogwai.platform.spi.PortId
import dev.hogwai.platform.spi.ProviderVersion
import dev.hogwai.platform.spi.SpiMajor
import dev.hogwai.platform.spi.provider.ProviderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

class KotlinOrderSummaryProviderFactoryTest {

    @Test
    fun exposesTheOrderSummaryDescriptor() {
        val descriptor = KotlinOrderSummaryProviderFactory().descriptor()

        assertThat(descriptor.providerId().value).isEqualTo("demo.kotlin.order-summary")
        assertThat(descriptor.version()).isEqualTo(ProviderVersion.parse("1.0.0"))
        assertThat(descriptor.capabilityKind()).isEqualTo(CapabilityKind.SOURCE)
        assertThat(descriptor.spiMajor()).isEqualTo(SpiMajor.V1)
        assertThat(descriptor.outputPorts()[PortId("records")]!!.schema().fields().map { it.id().value() })
            .containsExactly("orderId", "orderedQuantity", "deliveredQuantity", "deliveryPercent", "status")
    }

    @Test
    fun registersWithServiceLoader() {
        assertThat(ServiceLoader.load(ProviderFactory::class.java).toList())
            .anyMatch { it is KotlinOrderSummaryProviderFactory }
    }
}
