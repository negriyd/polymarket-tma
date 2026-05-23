package com.polymarket.tma.redeem;

import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

/**
 * Encodes the calldata for {@code ConditionalTokens.redeemPositions(IERC20,bytes32,bytes32,uint256[])}.
 *
 * <p>The Polymarket CTF (Polygon address {@code 0x4D97DCd97eC945f40cF65F87097ACe5EA0476045}) implements
 * the standard Gnosis Conditional Tokens Framework. Once a market is reported (resolved) the user can
 * burn outcome tokens and receive USDC for the winning index set:
 *
 * <pre>
 * function redeemPositions(
 *     IERC20 collateralToken,    // USDC on Polygon
 *     bytes32 parentCollectionId,// 0x0 for top-level binary markets (Polymarket)
 *     bytes32 conditionId,       // market condition id
 *     uint256[] indexSets        // bitmask of outcome indices to redeem (1 << outcomeIndex)
 * ) external;
 * </pre>
 *
 * <p>Pure function — no RPC. The frontend takes the resulting calldata and dispatches it as a regular
 * Polygon transaction via Privy {@code useSendTransaction}.
 */
public final class RedeemCalldataBuilder {

    /** All-zeros bytes32 used as {@code parentCollectionId} for top-level Polymarket markets. */
    public static final byte[] EMPTY_BYTES32 = new byte[32];

    private RedeemCalldataBuilder() {}

    /**
     * Build the {@code redeemPositions(address,bytes32,bytes32,uint256[])} calldata.
     *
     * @param collateralToken  USDC contract address (collateral on Polymarket).
     * @param parentCollectionId 32-byte hex (with or without {@code 0x}). Pass {@code null} for top-level.
     * @param conditionId      condition id hex string (66 chars including {@code 0x}).
     * @param indexSets        list of outcome bitmasks ({@code 1 << index}). Must be non-empty.
     */
    public static String redeemPositions(String collateralToken,
                                          String parentCollectionId,
                                          String conditionId,
                                          List<BigInteger> indexSets) {
        if (collateralToken == null || collateralToken.isBlank()) {
            throw new IllegalArgumentException("collateralToken is required");
        }
        if (conditionId == null || conditionId.isBlank()) {
            throw new IllegalArgumentException("conditionId is required");
        }
        if (indexSets == null || indexSets.isEmpty()) {
            throw new IllegalArgumentException("indexSets must contain at least one entry");
        }

        byte[] parent = parentCollectionId == null || parentCollectionId.isBlank()
                ? EMPTY_BYTES32
                : padTo32(Numeric.hexStringToByteArray(parentCollectionId));
        byte[] cond = padTo32(Numeric.hexStringToByteArray(conditionId));

        DynamicArray<Uint256> sets = new DynamicArray<>(
                Uint256.class,
                indexSets.stream().map(Uint256::new).toList());

        Function fn = new Function(
                "redeemPositions",
                List.of(
                        new Address(collateralToken),
                        new Bytes32(parent),
                        new Bytes32(cond),
                        sets),
                List.<TypeReference<?>>of());
        return FunctionEncoder.encode(fn);
    }

    /** {@code 1 << outcomeIndex} — single-outcome convenience. */
    public static BigInteger indexSetFor(int outcomeIndex) {
        if (outcomeIndex < 0 || outcomeIndex > 255) {
            throw new IllegalArgumentException("outcomeIndex out of range: " + outcomeIndex);
        }
        return BigInteger.ONE.shiftLeft(outcomeIndex);
    }

    private static byte[] padTo32(byte[] in) {
        if (in.length == 32) {
            return in;
        }
        if (in.length > 32) {
            throw new IllegalArgumentException("bytes32 input is longer than 32 bytes");
        }
        byte[] out = new byte[32];
        System.arraycopy(in, 0, out, 32 - in.length, in.length);
        return out;
    }
}
