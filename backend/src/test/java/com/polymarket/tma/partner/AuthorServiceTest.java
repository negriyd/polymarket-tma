package com.polymarket.tma.partner;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.auth.entity.AppUser;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthorServiceTest {

    @Test
    void weekStartsOnMondayUtc() {
        assertThat(AuthorService.weekStart(Instant.parse("2026-10-11T17:00:00Z")))
                .as("Sunday").isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
        assertThat(AuthorService.weekStart(Instant.parse("2026-10-12T00:00:00Z")))
                .as("Monday midnight").isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
    }

    @Test
    void publicNamesPreferUsernameThenFirstNameAndInitial() {
        AppUser u = new AppUser();
        u.setFirstName("Bob");
        u.setLastName("Smith");
        assertThat(AuthorService.publicUserName(u)).isEqualTo("Bob S.");
        u.setUsername("bobby");
        assertThat(AuthorService.publicUserName(u)).isEqualTo("@bobby");
        assertThat(AuthorService.publicUserName(new AppUser())).isEqualTo("—");

        var terms = new PartnerRepository.Terms(1, 5000, null, true, null, "Канал X", Instant.now());
        assertThat(AuthorService.displayName(u, terms)).isEqualTo("Канал X");
        var noName = new PartnerRepository.Terms(1, 5000, null, true, null, " ", Instant.now());
        assertThat(AuthorService.displayName(u, noName)).isEqualTo("@bobby");
    }
}
