package com.milestonepay.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fake AI that answers contract call 1 with a fixed, believable checklist, so the backend and the
 * screens can be built before the real AI service exists. Same brief in, same criteria out.
 * <p>
 * A few keywords in the brief add matching checks, which is enough for the demo's landing page
 * with a contact form. The {@code checkMethod} names are placeholders: their list isn't agreed yet.
 */
@Component
@ConditionalOnProperty(name = "milestonepay.ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    @Override
    public CriteriaResponse generateCriteria(CriteriaRequest request) {
        String brief = request.brief() == null ? "" : request.brief().toLowerCase(Locale.ROOT);
        List<CriteriaResponse.Criterion> out = new ArrayList<>();
        add(out, "The submitted URL loads and returns HTTP 200", "objective", "http_status");
        if (brief.contains("form") || brief.contains("contact")) {
            add(out, "The contact form submits successfully and shows a confirmation", "objective", "form_submit");
        }
        if (brief.contains("mobile") || brief.contains("responsive")) {
            add(out, "The page has no horizontal scroll at 375px width", "objective", "viewport_check");
        }
        if (brief.contains("fast") || brief.contains("speed") || brief.contains("load")) {
            add(out, "The page loads in under 3 seconds", "objective", "load_time");
        }
        add(out, "No broken links on the page", "objective", "link_check");
        add(out, "The design matches the look and feel described in the brief", "subjective", "client_judgement");
        return new CriteriaResponse(out);
    }

    private static void add(List<CriteriaResponse.Criterion> out, String text, String type, String checkMethod) {
        out.add(new CriteriaResponse.Criterion("c" + (out.size() + 1), text, type, checkMethod));
    }
}
