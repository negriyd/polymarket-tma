package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Polymarket Gamma encodes several array-like fields ({@code outcomes}, {@code outcomePrices},
 * {@code clobTokenIds}) as JSON strings rather than nested arrays. This deserializer accepts
 * either form and returns a {@code List<String>}.
 */
public class PolymarketStringListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.START_ARRAY) {
            List<String> out = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                out.add(p.getValueAsString());
            }
            return out;
        }
        if (token == JsonToken.VALUE_STRING) {
            String raw = p.getValueAsString();
            if (raw == null || raw.isBlank()) return List.of();
            ObjectMapper mapper = (ObjectMapper) p.getCodec();
            JsonNode node = mapper.readTree(raw);
            if (!node.isArray()) {
                return List.of(raw);
            }
            List<String> out = new ArrayList<>(node.size());
            for (JsonNode el : node) {
                out.add(el.asText());
            }
            return out;
        }
        return ctxt.handleUnexpectedToken(List.class, p) instanceof List<?> list ? cast(list) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> cast(List<?> list) {
        return (List<String>) list;
    }
}
