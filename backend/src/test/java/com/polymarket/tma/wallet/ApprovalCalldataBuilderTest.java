package com.polymarket.tma.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ApprovalCalldataBuilderTest {

    private static final String SPENDER = "0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E";

    @Test
    void erc20ApproveSelectorAndPaddedArgs() {
        String calldata = ApprovalCalldataBuilder.erc20Approve(
                SPENDER, ApprovalCalldataBuilder.MAX_UINT256);

        assertThat(calldata).startsWith("0x095ea7b3");
        assertThat(calldata).hasSize(2 + 8 + 64 + 64);
        assertThat(calldata.toLowerCase()).contains(SPENDER.substring(2).toLowerCase());
        assertThat(calldata).endsWith("f".repeat(64));
    }

    @Test
    void erc20ApproveAmountIsCorrect() {
        BigInteger amount = BigInteger.valueOf(123_456L);
        String calldata = ApprovalCalldataBuilder.erc20Approve(SPENDER, amount);
        String amountHex = String.format("%064x", amount);
        assertThat(calldata.toLowerCase()).endsWith(amountHex);
    }

    @Test
    void erc1155SetApprovalForAllSelectorAndArgs() {
        String enable = ApprovalCalldataBuilder.erc1155SetApprovalForAll(SPENDER, true);
        String disable = ApprovalCalldataBuilder.erc1155SetApprovalForAll(SPENDER, false);

        assertThat(enable).startsWith("0xa22cb465");
        assertThat(disable).startsWith("0xa22cb465");
        assertThat(enable).hasSize(2 + 8 + 64 + 64);
        assertThat(enable).endsWith("0".repeat(63) + "1");
        assertThat(disable).endsWith("0".repeat(64));
        assertThat(enable.toLowerCase()).contains(SPENDER.substring(2).toLowerCase());
    }

    @Test
    void maxUint256Is2Pow256Minus1() {
        assertThat(ApprovalCalldataBuilder.MAX_UINT256)
                .isEqualTo(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE));
        assertThat(ApprovalCalldataBuilder.MAX_UINT256.toString(16))
                .isEqualTo("f".repeat(64));
    }
}
