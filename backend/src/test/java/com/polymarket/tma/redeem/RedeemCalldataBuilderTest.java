package com.polymarket.tma.redeem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedeemCalldataBuilderTest {

    private static final String USDC = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174";
    private static final String CONDITION_ID =
            "0x9bff6c4f1c5e9a4ef1f9c6d3a7e7c2c0d8b1c2c4e1c2c4e1c2c4e1c2c4e1c2c4";

    @Test
    void selectorAndArgsForBinaryYes() {
        String calldata = RedeemCalldataBuilder.redeemPositions(
                USDC, null, CONDITION_ID, List.of(BigInteger.ONE));

        // 0x + 4-byte selector + 4 head args (32 bytes each) + dynamic [length=1, value=1].
        assertThat(calldata).startsWith("0x").hasSize(2 + 8 + 64 * 6);
        assertThat(calldata.toLowerCase()).contains(USDC.substring(2).toLowerCase());
        assertThat(calldata.toLowerCase()).contains(CONDITION_ID.substring(2).toLowerCase());
        // Last 32 bytes encode the single index set value (== 1).
        assertThat(calldata).endsWith("0".repeat(63) + "1");
    }

    @Test
    void indexSetForOutcomeIndexIsBitMask() {
        assertThat(RedeemCalldataBuilder.indexSetFor(0)).isEqualTo(BigInteger.ONE);
        assertThat(RedeemCalldataBuilder.indexSetFor(1)).isEqualTo(BigInteger.TWO);
        assertThat(RedeemCalldataBuilder.indexSetFor(7)).isEqualTo(BigInteger.valueOf(128));
    }

    @Test
    void rejectsEmptyIndexSets() {
        assertThatThrownBy(() -> RedeemCalldataBuilder.redeemPositions(
                USDC, null, CONDITION_ID, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexSets");
    }

    @Test
    void rejectsBlankConditionId() {
        assertThatThrownBy(() -> RedeemCalldataBuilder.redeemPositions(
                USDC, null, "", List.of(BigInteger.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conditionId");
    }

    @Test
    void parentCollectionIdDefaultsToZero() {
        String withNull = RedeemCalldataBuilder.redeemPositions(
                USDC, null, CONDITION_ID, List.of(BigInteger.ONE));
        String withBlank = RedeemCalldataBuilder.redeemPositions(
                USDC, "", CONDITION_ID, List.of(BigInteger.ONE));
        String withZero = RedeemCalldataBuilder.redeemPositions(
                USDC, "0x" + "00".repeat(32), CONDITION_ID, List.of(BigInteger.ONE));

        assertThat(withNull).isEqualTo(withBlank).isEqualTo(withZero);
    }
}
