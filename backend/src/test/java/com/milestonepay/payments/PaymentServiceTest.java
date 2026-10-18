package com.milestonepay.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestonepay.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Runs PaymentService against a fake PayPal: checks what we send and how we read the answers. */
class PaymentServiceTest {

    private static final String BASE = "https://api-m.sandbox.paypal.com";
    // Obviously fake values: these tests never reach PayPal.
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";

    private MockRestServiceServer paypal;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        paypal = MockRestServiceServer.bindTo(builder).build();
        PayPalProperties props = new PayPalProperties(BASE, CLIENT_ID, CLIENT_SECRET,
                "http://localhost:8080/dev/payments/return", "http://localhost:8080/dev/payments/cancel",
                "http://localhost:8080/dev/payments/approved", "http://localhost:8080/dev/payments/cancelled");
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);
        service = new PaymentService(new PayPalClient(builder, props, new ObjectMapper(), clock));
    }

    private void expectToken() {
        paypal.expect(once(), requestTo(BASE + "/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + HttpHeaders.encodeBasicAuth(CLIENT_ID, CLIENT_SECRET, StandardCharsets.UTF_8)))
                .andRespond(withSuccess("{\"access_token\":\"T1\",\"expires_in\":32400}", MediaType.APPLICATION_JSON));
    }

    @Test
    void createsAuthorizeOrderWithIdempotencyKeyAndReturnsApproveLink() {
        expectToken();
        paypal.expect(requestTo(BASE + "/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer T1"))
                .andExpect(header("PayPal-Request-Id", "order-m1-1"))
                .andExpect(jsonPath("$.intent").value("AUTHORIZE"))
                .andExpect(jsonPath("$.purchase_units[0].amount.value").value("300.00"))
                .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("USD"))
                .andExpect(jsonPath("$.payment_source.paypal.experience_context.return_url")
                        .value("http://localhost:8080/dev/payments/return?milestoneId=m1"))
                .andRespond(withSuccess("""
                        {"id":"ORDER-TEST-1","status":"PAYER_ACTION_REQUIRED","links":[
                          {"rel":"self","href":"https://x/self"},
                          {"rel":"payer-action","href":"https://www.sandbox.paypal.com/checkoutnow?token=ORDER-TEST-1"}]}
                        """, MediaType.APPLICATION_JSON));

        CreatedOrder order = service.createAuthorizeOrder("m1", 1, Money.of(30000, "USD"), "Landing page",
                "http://localhost:8080/dev/payments/return", "http://localhost:8080/dev/payments/cancel");

        assertThat(order.orderId()).isEqualTo("ORDER-TEST-1");
        assertThat(order.approveUrl()).contains("checkoutnow?token=ORDER-TEST-1");
        paypal.verify();
    }

    @Test
    void authorizeReadsHoldAndReusesCachedToken() {
        expectToken(); // only once for two calls
        paypal.expect(requestTo(BASE + "/v2/checkout/orders/ORDER1/authorize"))
                .andExpect(header("PayPal-Request-Id", "authorize-m1-1"))
                .andRespond(withSuccess("""
                        {"id":"ORDER1","status":"COMPLETED","purchase_units":[{"payments":{"authorizations":[
                          {"id":"AUTH1","status":"CREATED","amount":{"currency_code":"USD","value":"300.00"},
                           "expiration_time":"2026-11-08T10:00:00Z"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        paypal.expect(requestTo(BASE + "/v2/payments/authorizations/AUTH1/capture"))
                .andExpect(header("Authorization", "Bearer T1"))
                .andExpect(header("PayPal-Request-Id", "capture-m1-1"))
                .andExpect(content().json("{\"final_capture\":true}"))
                .andRespond(withSuccess("""
                        {"id":"CAP1","status":"COMPLETED","amount":{"currency_code":"USD","value":"300.00"}}
                        """, MediaType.APPLICATION_JSON));

        AuthorizationResult auth = service.authorizeOrder("m1", 1, "ORDER1");
        CaptureResult capture = service.capture("m1", 1, auth.authorizationId());

        assertThat(auth.status()).isEqualTo("CREATED");
        assertThat(auth.amount()).isEqualTo(Money.of(30000, "USD"));
        assertThat(auth.expiresAt()).isEqualTo(Instant.parse("2026-11-08T10:00:00Z"));
        assertThat(capture.captureId()).isEqualTo("CAP1");
        assertThat(capture.amount()).isEqualTo(Money.of(30000, "USD"));
        paypal.verify();
    }

    @Test
    void voidWithEmptyBodyFallsBackToReadingTheAuthorization() {
        expectToken();
        paypal.expect(requestTo(BASE + "/v2/payments/authorizations/AUTH1/void"))
                .andExpect(header("PayPal-Request-Id", "void-m1-1"))
                .andRespond(withNoContent());
        paypal.expect(requestTo(BASE + "/v2/payments/authorizations/AUTH1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"AUTH1","status":"VOIDED","amount":{"currency_code":"USD","value":"150.00"}}
                        """, MediaType.APPLICATION_JSON));

        AuthorizationResult voided = service.voidAuthorization("m1", 1, "AUTH1");

        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(voided.expiresAt()).isNull();
        paypal.verify();
    }

    @Test
    void mapsPayPalErrorsToPayPalException() {
        expectToken();
        paypal.expect(requestTo(BASE + "/v2/checkout/orders/ORDER1/authorize"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"name":"UNPROCESSABLE_ENTITY","message":"The requested action could not be performed.",
                                 "debug_id":"abc123","details":[{"issue":"ORDER_NOT_APPROVED"}]}
                                """));

        assertThatThrownBy(() -> service.authorizeOrder("m1", 1, "ORDER1"))
                .isInstanceOfSatisfying(PayPalException.class, e -> {
                    assertThat(e.httpStatus()).isEqualTo(422);
                    assertThat(e.issue()).isEqualTo("ORDER_NOT_APPROVED");
                    assertThat(e.debugId()).isEqualTo("abc123");
                });
    }

    @Test
    void secondAttemptGetsItsOwnRequestIdSoPayPalDoesNotReplayTheOldOrder() {
        expectToken();
        paypal.expect(requestTo(BASE + "/v2/checkout/orders"))
                .andExpect(header("PayPal-Request-Id", "order-m1-2"))
                .andExpect(jsonPath("$.payment_source.paypal.experience_context.return_url")
                        .value("http://localhost:8080/dev/payments/approved?milestoneId=m1"))
                .andRespond(withSuccess("""
                        {"id":"ORDER-TEST-2","status":"PAYER_ACTION_REQUIRED","links":[
                          {"rel":"payer-action","href":"https://www.sandbox.paypal.com/checkoutnow?token=ORDER-TEST-2"}]}
                        """, MediaType.APPLICATION_JSON));

        CreatedOrder order = service.createAuthorizeOrder("m1", 2, Money.of(30000, "USD"), "Landing page",
                "http://localhost:8080/dev/payments/approved", "http://localhost:8080/dev/payments/cancelled");

        assertThat(order.orderId()).isEqualTo("ORDER-TEST-2");
        paypal.verify();
    }

    @Test
    void rejectsAttemptOutOfRange() {
        assertThatThrownBy(() -> service.capture("m1", 0, "AUTH1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdsThatCouldChangeThePath() {
        assertThatThrownBy(() -> service.capture("m1", 1, "AUTH1/../../orders"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
