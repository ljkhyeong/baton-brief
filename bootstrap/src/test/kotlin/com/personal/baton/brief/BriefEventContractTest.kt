package com.personal.baton.brief

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.SpecificationVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class BriefEventContractTest {

    @Test
    fun `이벤트 v2 계약 예시는 JSON Schema와 일치한다`() {
        val schema = ClassPathResource("contracts/schemas/source-event.v2.schema.json").inputStream
            .use(SCHEMA_REGISTRY::getSchema)
            .also { it.initializeValidators() }

        EXAMPLES.forEach { fileName ->
            val example = ClassPathResource("contracts/examples/$fileName").inputStream
                .reader()
                .use { it.readText() }
            assertThat(schema.validate(example, InputFormat.JSON))
                .describedAs("%s 계약 검증", fileName)
                .isEmpty()
        }
    }

    companion object {
        private val EXAMPLES = listOf(
            "role-unassigned.active-r1-critical.json",
            "role-successor-missing.active-r1-warning.json",
            "role-preparation-incomplete.active-r1-warning.json",
            "routine-repeatedly-overdue.active-r1-critical.json",
            "handoff-incomplete.active-r1-warning.json",
            "role-unassigned.active-r2-warning.json",
            "role-unassigned.resolved-r3-warning.json",
        )
        private val SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
        ) { builder ->
            builder.schemaRegistryConfig(
                SchemaRegistryConfig.builder()
                    .formatAssertionsEnabled(true)
                    .build(),
            )
        }
    }
}
