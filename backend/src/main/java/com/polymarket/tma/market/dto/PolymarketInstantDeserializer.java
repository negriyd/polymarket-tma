package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Polymarket Gamma sometimes returns UTC instants with a space between date and time
 * (e.g. {@code 2026-05-12 12:25:00Z}) instead of ISO-8601 {@code T}. Default Jackson
 * {@link Instant} parsing rejects that form.
 */
public final class PolymarketInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token.isNumeric()) {
            return Instant.ofEpochMilli(p.getLongValue());
        }
        String raw = p.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = normalizeSpaceDateTime(raw);
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException ex) {
                throw InvalidFormatException.from(p, "Cannot parse Polymarket instant", raw, Instant.class);
            }
        }
    }

    /** Turn {@code yyyy-MM-dd HH:mm...} into {@code yyyy-MM-ddTHH:mm...} when the separator is a space. */
    static String normalizeSpaceDateTime(String raw) {
        if (raw.length() > 10 && raw.charAt(10) == ' ') {
            return raw.substring(0, 10) + 'T' + raw.substring(11);
        }
        return raw;
    }
}
