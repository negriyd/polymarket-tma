package com.polymarket.tma.predictions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionServiceTest {

    @Test
    void sideMapsToTheMatchingOutcome() {
        assertThat(PredictionService.outcomeIndex(List.of("Yes", "No"), "yes")).isEqualTo(0);
        assertThat(PredictionService.outcomeIndex(List.of("No", "Yes"), "yes")).isEqualTo(1);
        assertThat(PredictionService.outcomeIndex(List.of("Texas", "Oklahoma"), "no")).as("named outcomes").isEqualTo(1);
        assertThat(PredictionService.outcomeIndex(null, "yes")).isEqualTo(0);
    }
}
