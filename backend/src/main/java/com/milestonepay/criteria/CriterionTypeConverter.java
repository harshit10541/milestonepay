package com.milestonepay.criteria;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link CriterionType} as {@code objective} / {@code subjective}, matching the CHECK in V2. */
@Converter(autoApply = true)
class CriterionTypeConverter implements AttributeConverter<CriterionType, String> {

    @Override
    public String convertToDatabaseColumn(CriterionType type) {
        return type == null ? null : type.dbValue();
    }

    @Override
    public CriterionType convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        CriterionType type = CriterionType.parse(value);
        if (type == null) {
            throw new IllegalStateException("Unknown criterion type in database: " + value);
        }
        return type;
    }
}
