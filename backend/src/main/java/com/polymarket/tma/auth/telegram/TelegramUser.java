package com.polymarket.tma.auth.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUser(
        @JsonProperty("id") Long id,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("username") String username,
        @JsonProperty("language_code") String languageCode,
        @JsonProperty("is_premium") Boolean premium,
        @JsonProperty("photo_url") String photoUrl,
        @JsonProperty("allows_write_to_pm") Boolean allowsWriteToPm
) {
}
