package com.personal.baton.brief

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.SpecificationVersion
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventSeverity
import com.personal.baton.brief.domain.SourceEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

class BriefEventContractTest {

    @Test
    fun `이벤트 v2 계약 예시는 JSON Schema와 일치한다`() {
        assertThat(EXAMPLES).isNotEmpty
        EXAMPLES.forEach { resource ->
            val example = resource.getContentAsString(Charsets.UTF_8)
            assertThat(SCHEMA.validate(example, InputFormat.JSON))
                .describedAs("%s 계약 검증", resource.filename)
                .isEmpty()
        }
    }

    @Test
    fun `aggregateRevision은 부호 있는 64비트 양수 범위만 허용한다`() {
        val validExample = EXAMPLE_DOCUMENT.deepCopy().put("aggregateRevision", Long.MAX_VALUE)
        val invalidExample = EXAMPLE_DOCUMENT.deepCopy()
            .put("aggregateRevision", "9223372036854775808".toBigInteger())

        assertThat(SCHEMA.validate(JSON.writeValueAsString(validExample), InputFormat.JSON))
            .isEmpty()
        assertThat(SCHEMA.validate(JSON.writeValueAsString(invalidExample), InputFormat.JSON))
            .isNotEmpty()
    }

    @Test
    fun `UUID와 시점 format을 실제 제약으로 검증한다`() {
        listOf(
            "eventId" to "not-a-uuid",
            "eventId" to "AAAAAAAAAAAAAAAAAAAAAA",
            "occurredAt" to "2026-02-30T09:00:00Z",
        ).forEach { (field, value) ->
            val invalidExample = EXAMPLE_DOCUMENT.deepCopy().put(field, value)
            assertThat(SCHEMA.validate(JSON.writeValueAsString(invalidExample), InputFormat.JSON))
                .isNotEmpty()
        }
    }

    @Test
    fun `이벤트 v2 종류는 도메인과 Schema와 예시에 모두 일치한다`() {
        val domainEventTypes = SourceEventType.entries
            .filter { SourceEvent.isReceivable(2, it, SourceEventSeverity.CRITICAL) }
            .mapTo(mutableSetOf(), SourceEventType::name)
        val schemaEventTypes = SCHEMA_DOCUMENT
            .path("properties")
            .path("eventType")
            .path("enum")
            .mapTo(mutableSetOf()) { it.stringValue() }
        val exampleEventTypes = EXAMPLES.mapTo(mutableSetOf()) { resource ->
            JSON.readTree(resource.getContentAsString(Charsets.UTF_8))
                .path("eventType")
                .stringValue()
        }

        assertThat(schemaEventTypes).isEqualTo(domainEventTypes)
        assertThat(exampleEventTypes).containsAll(domainEventTypes)
    }

    @Test
    fun `sourceReference는 원문을 보존할 수 있는 문자와 같은 공백 기준을 사용한다`() {
        listOf("\u00a0", "\nreference", "😀".repeat(128)).forEach { sourceReference ->
            val validExample = EXAMPLE_DOCUMENT.deepCopy().put("sourceReference", sourceReference)
            assertThat(SCHEMA.validate(JSON.writeValueAsString(validExample), InputFormat.JSON))
                .isEmpty()
        }
        listOf("", " \t\n", "\u2003", "\u0000", "valid\u0000suffix", "\uD800", "\uDC00")
            .forEach { sourceReference ->
                val invalidExample = EXAMPLE_DOCUMENT.deepCopy().put("sourceReference", sourceReference)
                assertThat(SCHEMA.validate(JSON.writeValueAsString(invalidExample), InputFormat.JSON))
                    .isNotEmpty()
            }
    }

    companion object {
        private val JSON = JsonMapper.builder().build()
        private val SCHEMA_RESOURCE = ClassPathResource("contracts/schemas/source-event.v2.schema.json")
        private val SCHEMA_DOCUMENT = SCHEMA_RESOURCE.inputStream.use(JSON::readTree)
        private val SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
        ) { builder ->
            builder.schemaRegistryConfig(
                SchemaRegistryConfig.builder()
                    .formatAssertionsEnabled(true)
                    .build(),
            )
        }
        private val SCHEMA = SCHEMA_RESOURCE
            .inputStream
            .use(SCHEMA_REGISTRY::getSchema)
        private val EXAMPLES = PathMatchingResourcePatternResolver()
            .getResources("classpath*:contracts/examples/*.json")
            .sortedBy { it.filename }
        private val EXAMPLE_DOCUMENT = EXAMPLES.first().inputStream.use(JSON::readTree) as ObjectNode
    }
}
