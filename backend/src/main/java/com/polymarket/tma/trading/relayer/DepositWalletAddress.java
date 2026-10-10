package com.polymarket.tma.trading.relayer;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

/**
 * Deterministic CREATE2 address of a user's Polymarket deposit wallet.
 *
 * <p>Port of {@code deriveBeaconDepositWallet} / {@code deriveUupsDepositWallet} from
 * {@code @polymarket/builder-relayer-client}: the factory clones a Solady ERC-1967 (beacon) proxy
 * with immutable args {@code abi.encode(factory, bytes32(owner))}, salted by their keccak.
 * The address is known before deployment, so users can be shown it (and fund it) up front.
 */
public final class DepositWalletAddress {

    private static final byte[] ERC1967_CONST1 =
            hex("cc3735a920a3ca505d382bbc545af43d6000803e6038573d6000fd5b3d6000f3");
    private static final byte[] ERC1967_CONST2 =
            hex("5155f3363d3d373d3d363d7f360894a13ba1a3210667c828492db98dca3e2076");
    private static final BigInteger ERC1967_PREFIX = new BigInteger("61003d3d8160233d3973", 16);

    private static final byte[] BEACON_CONST1 =
            hex("b3582b35133d50545afa5036515af43d6000803e604d573d6000fd5b3d6000f3");
    private static final byte[] BEACON_CONST2 =
            hex("1b60e01b36527fa3f0ad74e5423aebfd80d3ef4346578335a9a72aeaee59ff6c");
    private static final byte[] BEACON_CONST3 = hex("60195155f3363d3d373d3d363d602036600436635c60da");
    private static final BigInteger BEACON_PREFIX = new BigInteger("6100523d8160233d3973", 16);

    private DepositWalletAddress() {}

    /** Beacon-proxy deposit wallet (the default for wallets deployed since 2026-06-29). */
    public static String beacon(String owner, String factory, String beacon) {
        byte[] args = args(owner, factory);
        byte[] initCodeHash = Hash.sha3(concat(
                prefix(BEACON_PREFIX, args.length), address(beacon),
                BEACON_CONST3, BEACON_CONST2, BEACON_CONST1, args));
        return create2(factory, Hash.sha3(args), initCodeHash);
    }

    /** Legacy UUPS deposit wallet (deployed before the factory switched to a beacon). */
    public static String uups(String owner, String factory, String implementation) {
        byte[] args = args(owner, factory);
        byte[] initCodeHash = Hash.sha3(concat(
                prefix(ERC1967_PREFIX, args.length), address(implementation),
                hex("6009"), ERC1967_CONST2, ERC1967_CONST1, args));
        return create2(factory, Hash.sha3(args), initCodeHash);
    }

    /** {@code abi.encode(address factory, bytes32 walletId)} with {@code walletId = bytes32(owner)}. */
    private static byte[] args(String owner, String factory) {
        return concat(word(factory), word(owner));
    }

    /** Solady {@code LibClone}: 10-byte creation prefix with the immutable-args length folded in. */
    private static byte[] prefix(BigInteger base, int argsLength) {
        return Numeric.toBytesPadded(base.add(BigInteger.valueOf(argsLength).shiftLeft(56)), 10);
    }

    private static String create2(String deployer, byte[] salt, byte[] initCodeHash) {
        byte[] hash = Hash.sha3(concat(new byte[] {(byte) 0xff}, address(deployer), salt, initCodeHash));
        return Keys.toChecksumAddress(Numeric.toHexString(Arrays.copyOfRange(hash, 12, 32)));
    }

    private static byte[] address(String address) {
        return Numeric.toBytesPadded(Numeric.toBigInt(address), 20);
    }

    private static byte[] word(String address) {
        return Numeric.toBytesPadded(Numeric.toBigInt(address), 32);
    }

    private static byte[] hex(String hex) {
        return Numeric.hexStringToByteArray(hex);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
