package com.milestonepay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the backend in step with {@code contracts/}: the examples match their schemas, the backend records
 * read the examples, and {@link MockAiClient} answers only what the schema allows.
 */
class ContractFilesTest {

    private static final Path CONTRACTS = Path.of("..", "contracts");
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "criteria-request, criteria-request",
            "criteria-response, criteria-response",
            "review-request, review-request",
            "review-response, review-response.pass",
            "review-response, review-response.broken-form",
    })
    void examplesMatchTheirSchemas(String schema, String example) throws IOException {
        assertThat(validate(schema, read("examples/" + example + ".example.json"))).isEmpty();
    }

    @Test
    void backendRecordsReadTheCriteriaExamples() throws IOException {
        CriteriaRequest request = JSON.treeToValue(read("examples/criteria-request.example.json"), CriteriaRequest.class);
        assertThat(request.brief()).contains("contact form");

        CriteriaResponse response = JSON.treeToValue(read("examples/criteria-response.example.json"),
                CriteriaResponse.class);
        assertThat(response.criteria()).extracting(CriteriaResponse.Criterion::checkMethod)
                .allSatisfy(m -> assertThat(CheckMethod.ALL).contains(m));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Landing page with a pricing section and a working contact form",
            "Fast, responsive page with a contact form and prices",
            "A blog",
    })
    void mockAnswersMatchTheSchema(String brief) {
        CriteriaResponse r = new MockAiClient().generateCriteria(new CriteriaRequest(
                "5b0f7c1e-2d7a-4c51-9a3e-0f4e1d2b6a10", "9c3a2e44-81f0-4b7e-a6d2-3e5f7a9b1c20", brief));
        assertThat(validate("criteria-response", JSON.valueToTree(r))).isEmpty();
    }

    @Test
    void checkMethodListMatchesTheSchema() throws IOException {
        Set<String> inSchema = new HashSet<>();
        read("schemas/criteria-response.schema.json").at("/$defs/checkMethod/enum").forEach(n -> inSchema.add(n.asText()));
        assertThat(inSchema).isEqualTo(CheckMethod.ALL);
    }

    /** The criterion definition is repeated in call 1's response and call 2's request; the copies must match. */
    @Test
    void criterionDefinitionIsTheSameInBothCalls() throws IOException {
        assertThat(read("schemas/review-request.schema.json").get("$defs"))
                .isEqualTo(read("schemas/criteria-response.schema.json").get("$defs"));
    }

    @Test
    void schemaRejectsCheckMethodsThatDoNotFitTheType() {
        String subjectiveWithBrowserCheck = """
                {"criteria":[{"id":"c1","text":"Looks modern","type":"subjective","checkMethod":"browser_form"}]}""";
        String objectiveWithClientReview = """
                {"criteria":[{"id":"c1","text":"Form submits","type":"objective","checkMethod":"client_review"}]}""";
        String unknownMethod = """
                {"criteria":[{"id":"c1","text":"Loads","type":"objective","checkMethod":"load_time"}]}""";
        for (String body : new String[]{subjectiveWithBrowserCheck, objectiveWithClientReview, unknownMethod}) {
            assertThat(validate("criteria-response", parse(body))).as(body).isNotEmpty();
        }
    }

    private static Set<ValidationMessage> validate(String schemaName, JsonNode document) {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(read("schemas/" + schemaName + ".schema.json"), config);
        return schema.validate(document);
    }

    private static JsonNode read(String relative) {
        try {
            return JSON.readTree(Files.readString(CONTRACTS.resolve(relative)));
        } catch (IOException e) {
            throw new IllegalStateException("Can't read contracts/" + relative, e);
        }
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
