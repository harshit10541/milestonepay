package com.milestonepay.criteria;

import com.milestonepay.ai.AiClientException;
import com.milestonepay.ai.CriteriaResponse;
import com.milestonepay.ai.CriteriaResponse.Criterion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The AI's answer is untrusted: anything off-contract is refused before it reaches the database. */
class CriteriaValidationTest {

    private static final Criterion GOOD = new Criterion("c1", "Form submits", "objective", "form_submit");

    @Test
    void acceptsAValidResponse() {
        List<Criterion> list = List.of(GOOD, new Criterion("c2", "Looks modern", "subjective", null));
        assertThat(CriteriaService.validate(new CriteriaResponse(list))).isEqualTo(list);
    }

    @Test
    void refusesEmptyOrMissing() {
        assertRefused(null);
        assertRefused(new CriteriaResponse(null));
        assertRefused(new CriteriaResponse(List.of()));
        assertRefused(new CriteriaResponse(Arrays.asList(GOOD, null)));
    }

    @Test
    void refusesUnknownType() {
        assertRefused(response(new Criterion("c1", "Form submits", "maybe", null)));
        assertRefused(response(new Criterion("c1", "Form submits", "OBJECTIVE", null)));
        assertRefused(response(new Criterion("c1", "Form submits", null, null)));
    }

    @Test
    void refusesMissingTextOrId() {
        assertRefused(response(new Criterion("c1", " ", "objective", null)));
        assertRefused(response(new Criterion(null, "Form submits", "objective", null)));
        assertRefused(response(new Criterion("c".repeat(65), "Form submits", "objective", null)));
    }

    @Test
    void refusesDuplicateIds() {
        assertRefused(new CriteriaResponse(List.of(GOOD, new Criterion("c1", "Other", "objective", null))));
    }

    @Test
    void refusesTooMany() {
        List<Criterion> many = new ArrayList<>();
        for (int i = 0; i <= CriteriaService.MAX_CRITERIA; i++) {
            many.add(new Criterion("c" + i, "Check " + i, "objective", null));
        }
        assertRefused(new CriteriaResponse(many));
    }

    private static CriteriaResponse response(Criterion c) {
        return new CriteriaResponse(List.of(c));
    }

    private static void assertRefused(CriteriaResponse r) {
        assertThatThrownBy(() -> CriteriaService.validate(r)).isInstanceOf(AiClientException.class);
    }
}
