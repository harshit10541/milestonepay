package com.milestonepay.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestonepay.common.Money;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Funding over HTTP against a real Postgres, with PayPal replaced by a mock of {@link PaymentService}
 * (its own HTTP behaviour is covered by PaymentServiceTest). Runs only when TEST_DATABASE_URL is set;
 * see JobsApiIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class FundingApiIntegrationTest {

    private static final Money AMOUNT = Money.of(30000, "USD");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    PaymentService paypal;

    private String client;
    private String freelancer;
    private String milestone;

    @BeforeEach
    void approvedMilestone() throws Exception {
        client = createUser("client");
        freelancer = createUser("freelancer");
        milestone = createMilestone();
        mvc.perform(as(client, post("/milestones/" + milestone + "/criteria/generate"))).andExpect(status().isOk());
        mvc.perform(as(client, post("/milestones/" + milestone + "/criteria/approve"))).andExpect(status().isOk());
        mvc.perform(as(freelancer, post("/milestones/" + milestone + "/criteria/approve")))
                .andExpect(jsonPath("$.milestone.state").value("criteria_approved"));
    }

    @Test
    void fundApproveAndReturnMovesTheMilestoneToFunded() throws Exception {
        String orderId = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
        when(paypal.createAuthorizeOrder(eq(milestone), eq(1), eq(AMOUNT), anyString(), anyString(), anyString()))
                .thenReturn(new CreatedOrder(orderId, "PAYER_ACTION_REQUIRED", "https://paypal.test/approve/" + orderId));
        when(paypal.getOrderStatus(orderId)).thenReturn("PAYER_ACTION_REQUIRED");

        // Only the client funds.
        mvc.perform(as(freelancer, post("/milestones/" + milestone + "/fund"))).andExpect(status().isForbidden());
        fund().andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt").value(1))
                .andExpect(jsonPath("$.status").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.approveUrl").value("https://paypal.test/approve/" + orderId));
        // Pressing Fund again before approving gives the same order, not a second one.
        fund().andExpect(jsonPath("$.paypalOrderId").value(orderId));
        verify(paypal, times(1)).createAuthorizeOrder(any(), anyInt(), any(), any(), any(), any());

        // Not approved on PayPal yet: 409, nothing changes.
        when(paypal.authorizeOrder(milestone, 1, orderId))
                .thenThrow(new PayPalException(422, "ORDER_NOT_APPROVED", "dbg", "not approved"));
        paypalReturn(client, orderId).andExpect(status().isConflict());

        // Approved: the hold is placed and the milestone is funded.
        reset(paypal);
        when(paypal.authorizeOrder(milestone, 1, orderId)).thenReturn(
                new AuthorizationResult("AUTH-1-" + orderId, "CREATED", AMOUNT, Instant.parse("2026-11-15T00:00:00Z")));
        paypalReturn(freelancer, orderId).andExpect(status().isForbidden());
        paypalReturn(client, orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.milestone.state").value("funded"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.payment.authorizationId").value("AUTH-1-" + orderId));

        // Coming back twice (refresh) returns the same result without calling PayPal again.
        paypalReturn(client, orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.milestone.state").value("funded"));
        verify(paypal, times(1)).authorizeOrder(milestone, 1, orderId);

        mvc.perform(as(freelancer, get("/milestones/" + milestone))).andExpect(status().isOk())
                .andExpect(jsonPath("$.milestone.state").value("funded"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.payment.amountMinor").value(30000));
        fund().andExpect(status().isConflict());
    }

    @Test
    void deadOrderStartsASecondAttempt() throws Exception {
        String first = "ORDER-A-" + UUID.randomUUID().toString().substring(0, 8);
        String second = "ORDER-B-" + UUID.randomUUID().toString().substring(0, 8);
        when(paypal.createAuthorizeOrder(eq(milestone), eq(1), any(), any(), any(), any()))
                .thenReturn(new CreatedOrder(first, "PAYER_ACTION_REQUIRED", "https://paypal.test/" + first));
        when(paypal.createAuthorizeOrder(eq(milestone), eq(2), any(), any(), any(), any()))
                .thenReturn(new CreatedOrder(second, "PAYER_ACTION_REQUIRED", "https://paypal.test/" + second));
        when(paypal.getOrderStatus(first)).thenThrow(new PayPalException(404, "RESOURCE_NOT_FOUND", "dbg", "gone"));

        fund().andExpect(jsonPath("$.attempt").value(1));
        fund().andExpect(jsonPath("$.attempt").value(2)).andExpect(jsonPath("$.paypalOrderId").value(second));

        // The abandoned order can't be completed any more.
        paypalReturn(client, first).andExpect(status().isConflict());
        verify(paypal, never()).authorizeOrder(any(), anyInt(), eq(first));
    }

    @Test
    void wrongHeldAmountIsVoidedAndNotFunded() throws Exception {
        String orderId = "ORDER-C-" + UUID.randomUUID().toString().substring(0, 8);
        when(paypal.createAuthorizeOrder(eq(milestone), eq(1), any(), any(), any(), any()))
                .thenReturn(new CreatedOrder(orderId, "PAYER_ACTION_REQUIRED", "https://paypal.test/" + orderId));
        when(paypal.authorizeOrder(milestone, 1, orderId))
                .thenReturn(new AuthorizationResult("AUTH-C-" + orderId, "CREATED", Money.of(100, "USD"), null));
        fund().andExpect(status().isOk());

        paypalReturn(client, orderId).andExpect(status().isBadGateway());

        verify(paypal).voidAuthorization(milestone, 1, "AUTH-C-" + orderId);
        mvc.perform(as(client, get("/milestones/" + milestone)))
                .andExpect(jsonPath("$.milestone.state").value("criteria_approved"))
                .andExpect(jsonPath("$.payment.status").value("ORDER_CREATED"));
    }

    @Test
    void cantFundBeforeCriteriaAreApproved() throws Exception {
        String draft = createMilestone();
        mvc.perform(as(client, post("/milestones/" + draft + "/fund"))).andExpect(status().isConflict());
        verify(paypal, never()).createAuthorizeOrder(any(), anyInt(), any(), any(), any(), any());
    }

    private ResultActions fund() throws Exception {
        return mvc.perform(as(client, post("/milestones/" + milestone + "/fund")));
    }

    private ResultActions paypalReturn(String user, String orderId) throws Exception {
        return mvc.perform(as(user, post("/payments/paypal/return")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\"}"));
    }

    private String createMilestone() throws Exception {
        String body = """
                {"title":"Landing page","freelancerId":"%s","milestones":[
                  {"title":"Landing page","brief":"A landing page with a working contact form","amountMinor":30000,"currency":"USD"}]}
                """.formatted(freelancer);
        String out = mvc.perform(as(client, post("/jobs")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode job = json.readTree(out);
        return job.get("milestones").get(0).get("id").asText();
    }

    private String createUser(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.test";
        String out = mvc.perform(post("/dev/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(out).get("id").asText();
    }

    private static MockHttpServletRequestBuilder as(String userId, MockHttpServletRequestBuilder req) {
        return req.header("X-User-Id", userId);
    }
}
