package com.milestonepay.jobs;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link MilestoneState} as its agreed lower-case name, matching the CHECK in V2. */
@Converter(autoApply = true)
class MilestoneStateConverter implements AttributeConverter<MilestoneState, String> {

    @Override
    public String convertToDatabaseColumn(MilestoneState state) {
        return state == null ? null : state.dbValue();
    }

    @Override
    public MilestoneState convertToEntityAttribute(String value) {
        return value == null ? null : MilestoneState.fromDb(value);
    }
}
