package com.milestonepay.criteria;

import java.util.UUID;

/** One checklist line as other modules and the API see it. */
public record CriterionView(UUID id, int position, String aiId, String text, CriterionType type,
                            String checkMethod) {
}
