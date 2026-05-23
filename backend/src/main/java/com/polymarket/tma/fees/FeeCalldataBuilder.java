package com.polymarket.tma.fees;

import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * Encodes the calldata for ERC-20 {@code transfer(address,uint256)} (selector {@code 0xa9059cbb}).
 *
 * <p>Used by the platform-fee flow: when a non-zero fee rate is configured the backend returns a
 * USDC transfer transaction the wallet broadcasts in addition to the trading order. Pure function —
 * no RPC.
 */
public final class FeeCalldataBuilder {

    private FeeCalldataBuilder() {}

    /** {@code transfer(recipient, amount)} on an ERC-20 token. */
    public static String erc20Transfer(String recipient, BigInteger amount) {
        Function fn = new Function(
                "transfer",
                List.of(new Address(recipient), new Uint256(amount)),
                List.of());
        return FunctionEncoder.encode(fn);
    }
}
