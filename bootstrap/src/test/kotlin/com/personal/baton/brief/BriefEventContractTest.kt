package com.personal.baton.brief

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.SpecificationVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

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

    companion object {
        private val SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
        ) { builder ->
            builder.schemaRegistryConfig(
                SchemaRegistryConfig.builder()
                    .formatAssertionsEnabled(true)
                    .build(),
            )
        }
        private val SCHEMA = ClassPathResource("contracts/schemas/source-event.v2.schema.json")
            .inputStream
            .use(SCHEMA_REGISTRY::getSchema)
        private val EXAMPLES = PathMatchingResourcePatternResolver()
            .getResources("classpath*:contracts/examples/*.json")
            .sortedBy { it.filename }
    }
}
