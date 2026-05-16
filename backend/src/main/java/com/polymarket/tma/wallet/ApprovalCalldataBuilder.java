package com.polymarket.tma.wallet;

import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * Encodes the calldata for ERC-20 {@code approve(address,uint256)} and
 * ERC-1155 {@code setApprovalForAll(address,bool)}. Pure functions — no RPC.
 *
 * <p>Polymarket trading requires:
 * <ul>
 *     <li>USDC.approve(CTFExchange, max) — so the exchange can pull collateral.</li>
 *     <li>ConditionalTokens.setApprovalForAll(CTFExchange, true) — so the exchange can move outcome tokens.</li>
 * </ul>
 *
 * <p>The frontend then sends each tx through Privy (wallet broadcasts to Polygon).
 */
public final class ApprovalCalldataBuilder {

    /** Standard "infinite" allowance value: 2^256 - 1. */
    public static final BigInteger MAX_UINT256 =
            BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    private ApprovalCalldataBuilder() {}

    /** {@code approve(spender, amount)} (selector {@code 0x095ea7b3}). */
    public static String erc20Approve(String spender, BigInteger amount) {
        Function fn = new Function(
                "approve",
                List.of(new Address(spender), new Uint256(amount)),
                List.of());
        return FunctionEncoder.encode(fn);
    }

    /** {@code setApprovalForAll(operator, approved)} (selector {@code 0xa22cb465}). */
    public static String erc1155SetApprovalForAll(String operator, boolean approved) {
        Function fn = new Function(
                "setApprovalForAll",
                List.of(new Address(operator), new Bool(approved)),
                List.of());
        return FunctionEncoder.encode(fn);
    }
}
