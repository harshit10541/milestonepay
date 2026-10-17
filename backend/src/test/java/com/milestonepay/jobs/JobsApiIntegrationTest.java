package com.milestonepay.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole step-2 flow over HTTP against a real Postgres (Flyway runs the migrations, Hibernate checks
 * the entities match them). Needs a throwaway database, so it only runs when TEST_DATABASE_URL is set:
 * <pre>
 * docker exec mp-db createdb -U postgres mp_test
 * TEST_DATABASE_URL=jdbc:postgresql://localhost:5434/mp_test ./mvnw test
 * </pre>
 * Events are append-only, so the test never cleans up; it uses fresh users each run instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JobsApiIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String client;
    private String freelancer;
    private String outsider;

    @BeforeEach
    void users() throws Exception {
        client = createUser("client");
        freelancer = createUser("freelancer");
        outsider = createUser("outsider");
    }

    @Test
    void createGenerateApproveAndRead() throws Exception {
        JsonNode job = createJob();
        String jobId = job.get("id").asText();
        String milestoneId = job.get("milestones").get(0).get("id").asText();
        assertThat(job.get("milestones").get(0).get("state").asText()).isEqualTo("draft");

        // Either party can read; anyone else gets 404.
        mvc.perform(as(freelancer, get("/jobs/" + jobId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.yourRole").value("freelancer"));
        mvc.perform(as(outsider, get("/jobs/" + jobId))).andExpect(status().isNotFound());
        mvc.perform(as(outsider, get("/milestones/" + milestoneId))).andExpect(status().isNotFound());

        // Nothing to approve yet; only the client generates.
        mvc.perform(as(client, post("/milestones/" + milestoneId + "/criteria/approve")))
                .andExpect(status().isConflict());
        mvc.perform(as(freelancer, post("/milestones/" + milestoneId + "/criteria/generate")))
                .andExpect(status().isForbidden());
        mvc.perform(as(client, post("/milestones/" + milestoneId + "/criteria/generate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria[0].aiId").value("c1"))
                .andExpect(jsonPath("$.criteria[?(@.checkMethod == 'form_submit')]").exists());

        // Regenerating clears an earlier approval.
        mvc.perform(as(client, post("/milestones/" + milestoneId + "/criteria/approve")))
                .andExpect(jsonPath("$.milestone.state").value("draft"));
        mvc.perform(as(client, post("/milestones/" + milestoneId + "/criteria/generate")))
                .andExpect(status().isOk());
        mvc.perform(as(client, get("/milestones/" + milestoneId)))
                .andExpect(jsonPath("$.milestone.clientCriteriaApprovedAt").doesNotExist());

        // Both approve: criteria_approved, and the checklist is frozen.
        mvc.perform(as(client, post("/milestones/" + milestoneId + "/criteria/approve")))
                .andExpect(jsonPath("$.milestone.state").value("draft"));
        mvc.perform(as(freelancer, post("/milestones/" + milestoneId + "/criteria/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestone.state").value("criteria_approved"));
        mvc.perform(as(client, post("/milestones/" + milestoneId + "/criteria/generate")))
                .andExpect(status().isConflict());

        mvc.perform(as(freelancer, get("/milestones/" + milestoneId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestone.state").value("criteria_approved"))
                .andExpect(jsonPath("$.milestone.amountMinor").value(30000))
                .andExpect(jsonPath("$.milestone.reviewWindowSeconds").value(300))
                .andExpect(jsonPath("$.milestone.revisionRoundsAllowed").value(2))
                .andExpect(jsonPath("$.criteria.length()").value(4));
    }

    @Test
    void refusesCallsWithoutAKnownCaller() throws Exception {
        mvc.perform(get("/jobs/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/jobs/" + UUID.randomUUID()).header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/jobs/" + UUID.randomUUID()).header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesTheJobBody() throws Exception {
        mvc.perform(as(client, post("/jobs")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"","freelancerId":"%s","milestones":[{"title":"x","brief":"y","amountMinor":0,"currency":"usd"}]}
                        """.formatted(freelancer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields['milestones[0].amountMinor']").exists())
                .andExpect(jsonPath("$.fields['milestones[0].currency']").exists());
        mvc.perform(as(client, post("/jobs")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"t","freelancerId":"%s","milestones":[{"title":"x","brief":"y","amountMinor":5,"currency":"USD"}]}
                        """.formatted(client)))
                .andExpect(status().isBadRequest());
    }

    private JsonNode createJob() throws Exception {
        String body = """
                {"title":"Landing page","freelancerId":"%s","milestones":[
                  {"title":"Landing page","brief":"A landing page with a working contact form","amountMinor":30000,"currency":"USD"},
                  {"title":"Blog","brief":"A blog section","amountMinor":15000,"currency":"USD","reviewWindowSeconds":600}]}
                """.formatted(freelancer);
        String out = mvc.perform(as(client, post("/jobs")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(out);
    }

    private String createUser(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.test";
        String out = mvc.perform(post("/dev/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(out).get("id").asText();
    }

    private static MockHttpServletRequestBuilder as(String userId, MockHttpServletRequestBuilder req) {
        return req.header("X-User-Id", userId);
    }
}
