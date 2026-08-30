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
        val template = EXAMPLES.first()
            .getContentAsString(Charsets.UTF_8)
            .replaceFirst(
                Regex("\"aggregateRevision\"\\s*:\\s*\\d+"),
                "\"aggregateRevision\": %s",
            )

        assertThat(SCHEMA.validate(template.format(Long.MAX_VALUE), InputFormat.JSON))
            .isEmpty()
        assertThat(SCHEMA.validate(template.format("9223372036854775808"), InputFormat.JSON))
            .isNotEmpty()
    }

    @Test
    fun `UUID와 시점 format을 실제 제약으로 검증한다`() {
        val template = EXAMPLES.first().getContentAsString(Charsets.UTF_8)
        val invalidExamples = listOf(
            template.replaceFirst(
                Regex("\"eventId\"\\s*:\\s*\"[^\"]+\""),
                "\"eventId\": \"not-a-uuid\"",
            ),
            template.replaceFirst(
                Regex("\"eventId\"\\s*:\\s*\"[^\"]+\""),
                "\"eventId\": \"AAAAAAAAAAAAAAAAAAAAAA\"",
            ),
            template.replaceFirst(
                Regex("\"occurredAt\"\\s*:\\s*\"[^\"]+\""),
                "\"occurredAt\": \"2026-02-30T09:00:00Z\"",
            ),
        )

        invalidExamples.forEach { example ->
            assertThat(SCHEMA.validate(example, InputFormat.JSON)).isNotEmpty()
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
    fun `sourceReference는 U+0000을 허용하지 않는다`() {
        val template = EXAMPLES.first().getContentAsString(Charsets.UTF_8)
        listOf("\\u0000", "valid\\u0000suffix").forEach { sourceReference ->
            val sourceReferenceField = checkNotNull(Regex(
                "\"sourceReference\"\\s*:\\s*\"[^\"]+\"",
            ).find(template))
            val invalidExample = template.replaceRange(
                sourceReferenceField.range,
                "\"sourceReference\": \"$sourceReference\"",
            )

            assertThat(SCHEMA.validate(invalidExample, InputFormat.JSON)).isNotEmpty()
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
    }
}
