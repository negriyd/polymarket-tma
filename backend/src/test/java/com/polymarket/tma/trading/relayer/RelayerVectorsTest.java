package com.polymarket.tma.trading.relayer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/**
 * Golden vectors produced by the official {@code @polymarket/builder-relayer-client} 0.0.10 /
 * {@code builder-signing-sdk} 0.0.8 ({@code tools/clob-v2-probe/gen-relayer-vectors.ts}).
 * Only public data: the Hardhat #0 test key and a throwaway HMAC secret.
 */
class RelayerVectorsTest {

    private static final String FACTORY = "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07";
    private static final String BEACON = "0x7A18EDfe055488A3128f01F563e5B479D92ffc3a";
    private static final String IMPLEMENTATION = "0x58CA52ebe0DadfdF531Cde7062e76746de4Db1eB";

    /** EOA whose deposit wallet was deployed via the relayer and traded on 2026-10-11. */
    private static final String PROBE_EOA = "0x415aE735eB964da56C11D0a53F0f082b85FcbEA5";
    private static final String PROBE_DEPOSIT_WALLET = "0xa1D17A5546aD9166Cb14071c36a5741d812CbE57";
    private static final String HARDHAT_0 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    @Test
    void beaconAddressMatchesDeployedWallet() {
        assertThat(DepositWalletAddress.beacon(PROBE_EOA, FACTORY, BEACON)).isEqualTo(PROBE_DEPOSIT_WALLET);
        assertThat(DepositWalletAddress.beacon(HARDHAT_0, FACTORY, BEACON))
                .isEqualTo("0xBc0fF067b7740Eff76C1ca93c875Ba6B890d6B50");
    }

    @Test
    void uupsAddressMatchesSdk() {
        assertThat(DepositWalletAddress.uups(PROBE_EOA, FACTORY, IMPLEMENTATION))
                .isEqualTo("0xBFDcF54F0ad18af4F8946C0A73604f0e136A90E4");
        assertThat(DepositWalletAddress.uups(HARDHAT_0, FACTORY, IMPLEMENTATION))
                .isEqualTo("0xdf8b9E8f9AB23f261F6e1B171B7454ae6E46Ba76");
    }

    @Test
    void batchTypedDataDigestAndSignatureMatchSdk() throws Exception {
        String wallet = "0xBc0fF067b7740Eff76C1ca93c875Ba6B890d6B50";
        var calls = List.of(
                new DepositWalletBatch.Call("0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB", "0",
                        "0x095ea7b3000000000000000000000000e111180000d2663c0091e4f400237545b87b996b"
                                + "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                new DepositWalletBatch.Call("0x4D97DCd97eC945f40cF65F87097ACe5EA0476045", "0",
                        "0xa22cb465000000000000000000000000e111180000d2663c0091e4f400237545b87b996b"
                                + "0000000000000000000000000000000000000000000000000000000000000001"));

        var typedData = DepositWalletBatch.typedData(wallet, "7", "1791671000", calls);
        byte[] digest = new StructuredDataEncoder(new ObjectMapper().writeValueAsString(typedData))
                .hashStructuredData();

        assertThat(Numeric.toHexString(digest))
                .isEqualTo("0x525a65571e5391de0a71368b74d6f70a276121e794bdefbc109da428675c05e2");

        byte[] sig = Numeric.hexStringToByteArray("0x37f6e18e8c915c8d8df2fdf4d2d98369b103b609171551e04f21ba68aa51b9df"
                + "309d6e6bfd17f60b06ac3b7472b13f9716566550504614fe1677e86133e8fab31c");
        var signatureData = new Sign.SignatureData(sig[64], Arrays.copyOfRange(sig, 0, 32), Arrays.copyOfRange(sig, 32, 64));
        assertThat("0x" + Keys.getAddress(Sign.signedMessageHashToKey(digest, signatureData)))
                .isEqualToIgnoringCase(HARDHAT_0);
    }

    @Test
    void builderHmacMatchesSdk() {
        String body = "{\"type\":\"WALLET-CREATE\",\"from\":\"0x415aE735eB964da56C11D0a53F0f082b85FcbEA5\","
                + "\"to\":\"0x00000000000Fb5C9ADea0298D729A0CB3823Cc07\"}";
        String signature = BuilderAuthSigner.signature(
                "dGVzdC1zZWNyZXQtZm9yLXZlY3RvcnMtb25seS0xMjM0NTY3OA==", 1791671000L, "POST", "/submit", body);

        assertThat(signature).isEqualTo("_chU-YLGgF2tyZF8YhcFwdjctF-dp-ArHvG2N_nVPpI=");
    }

    @Test
    void builderHeadersCarryCredentialsAndTimestamp() {
        var props = new RelayerProperties("https://relayer", "key", "dGVzdA==", "pass", FACTORY, BEACON, IMPLEMENTATION, null, null, null, null, null, null);

        var headers = BuilderAuthSigner.headers(props, 1791671000L, "POST", "/submit", "{}");

        assertThat(headers).containsEntry("POLY_BUILDER_API_KEY", "key")
                .containsEntry("POLY_BUILDER_PASSPHRASE", "pass")
                .containsEntry("POLY_BUILDER_TIMESTAMP", "1791671000")
                .containsKey("POLY_BUILDER_SIGNATURE");
    }
}
