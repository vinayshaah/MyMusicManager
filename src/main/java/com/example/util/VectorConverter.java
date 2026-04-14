package com.example.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.stream.Collectors;

@Converter
public class VectorConverter implements AttributeConverter<double[], String> {

    @Override
    public String convertToDatabaseColumn(double[] attribute) {
        if (attribute == null) return null;
        String values = Arrays.stream(attribute)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(","));
        return "[" + values + "]";
    }

    @Override
    public double[] convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String[] parts = dbData.replace("[", "").replace("]", "").split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }
}