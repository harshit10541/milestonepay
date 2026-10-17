package com.milestonepay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiClientTest {

    private final MockAiClient ai = new MockAiClient();

    @Test
    void contactFormBriefGetsAFormCheckAndOneSubjectiveCriterion() {
        CriteriaResponse r = ai.generateCriteria(new CriteriaRequest("j1", "m1",
                "Landing page with a working contact form"));

        assertThat(r.criteria()).extracting(CriteriaResponse.Criterion::checkMethod).contains("form_submit");
        assertThat(r.criteria()).filteredOn(c -> c.type().equals("subjective")).hasSize(1);
        assertThat(r.criteria()).allSatisfy(c -> assertThat(c.type()).isIn("objective", "subjective"));
    }

    @Test
    void sameBriefGivesSameCriteriaWithUniqueIds() {
        CriteriaRequest req = new CriteriaRequest("j1", "m1", "Fast, responsive page with a contact form");
        CriteriaResponse first = ai.generateCriteria(req);

        assertThat(ai.generateCriteria(req)).isEqualTo(first);
        Set<String> ids = new HashSet<>();
        first.criteria().forEach(c -> assertThat(ids.add(c.id())).as(c.id()).isTrue());
    }

    /** The JSON must use the agreed contract field names, since the real service will answer in JSON. */
    @Test
    void serializesWithTheContractFieldNames() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode request = json.valueToTree(new CriteriaRequest("j1", "m1", "brief"));
        assertThat(request.fieldNames()).toIterable().containsExactlyInAnyOrder("jobId", "milestoneId", "brief");

        JsonNode response = json.valueToTree(ai.generateCriteria(new CriteriaRequest("j1", "m1", "brief")));
        assertThat(response.fieldNames()).toIterable().containsExactly("criteria");
        assertThat(response.get("criteria").get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "text", "type", "checkMethod");
    }

    @Test
    void parsesTheContractSampleResponse() throws Exception {
        String sample = """
                {"criteria":[{"id":"c1","text":"Contact form submits","type":"objective","checkMethod":"form_submit"}]}
                """;
        CriteriaResponse r = new ObjectMapper().readValue(sample, CriteriaResponse.class);
        assertThat(r.criteria()).containsExactly(
                new CriteriaResponse.Criterion("c1", "Contact form submits", "objective", "form_submit"));
    }
}
