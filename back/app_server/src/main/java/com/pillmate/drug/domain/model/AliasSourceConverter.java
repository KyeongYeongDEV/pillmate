package com.pillmate.drug.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AliasSourceConverter implements AttributeConverter<AliasSource, String> {

    @Override
    public String convertToDatabaseColumn(AliasSource source) {
        if (source == null) return null;
        return source.name().toLowerCase();
    }

    @Override
    public AliasSource convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return AliasSource.valueOf(dbData.toUpperCase());
    }
}
