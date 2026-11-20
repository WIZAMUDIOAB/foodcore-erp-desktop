package com.foodcore.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Proveedor singleton thread-safe de Jackson {@link ObjectMapper} para FoodCore ERP.
 */
public final class JsonMapperProvider {

    private static final ObjectMapper MAPPER = construirMapper();

    private JsonMapperProvider() {
    }

    private static ObjectMapper construirMapper() {
        SimpleModule timeModule = new SimpleModule("FoodCoreJavaTime");
        timeModule.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(value.toString());
                }
            }
        });
        timeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getValueAsString();
                return (text == null || text.isBlank()) ? null : LocalDateTime.parse(text.trim());
            }
        });
        timeModule.addSerializer(LocalDate.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(value.toString());
                }
            }
        });
        timeModule.addDeserializer(LocalDate.class, new JsonDeserializer<>() {
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getValueAsString();
                return (text == null || text.isBlank()) ? null : LocalDate.parse(text.trim());
            }
        });
        timeModule.addSerializer(LocalTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalTime value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(value.toString());
                }
            }
        });
        timeModule.addDeserializer(LocalTime.class, new JsonDeserializer<>() {
            @Override
            public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getValueAsString();
                return (text == null || text.isBlank()) ? null : LocalTime.parse(text.trim());
            }
        });

        return new ObjectMapper()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(timeModule);
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
