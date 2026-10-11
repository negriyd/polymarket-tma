package com.polymarket.tma.trading.relayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.relayer.RelayerClient.RelayerTransaction;
import com.polymarket.tma.wallet.ApprovalCalldataBuilder;
import com.polymarket.tma.wallet.ApprovalStatusReader;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DepositWalletServiceTest {

    /** Hardhat account #0 — public test key. */
    private static final Credentials OWNER_KEY =
            Credentials.create("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final String OWNER = OWNER_KEY.getAddress();
    private static final String WALLET = "0xBc0fF067b7740Eff76C1ca93c875Ba6B890d6B50";
    private static final String PUSD = "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB";
    private static final String CTF = "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045";
    private static final String EXCHANGE = "0xE111180000d2663C0091e4f400237545B87B996B";

    @Mock private AppUserRepository userRepo;
    @Mock private RelayerClient relayer;
    @Mock private ApprovalStatusReader reader;
    @Mock private PendingBatchCache pending;

    private DepositWalletApprovals approvals;
    private DepositWalletService service;

    @BeforeEach
    void setUp() {
        AppProperties app = mock(AppProperties.class);
        when(app.polygon()).thenReturn(new AppProperties.Polygon("", "", EXCHANGE,
                "0xe2222d279d744050d28e00520010520000310F59", CTF));
        RelayerProperties relayerProps = new RelayerProperties("http://relayer", "k", "c2VjcmV0", "p",
                "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07", "0x7A18EDfe055488A3128f01F563e5B479D92ffc3a",
                "0x58CA52ebe0DadfdF531Cde7062e76746de4Db1eB", PUSD,
                "0xe3333700cA9d93003F00f0F71f8515005F6c00Aa", "0xd91E80cF2E7be2e162c6513ceD06f1dD0dA35296",
                "0x006F54F7f9A22e0000CC2AB60031000000ae9fEF", "0xAdA100Db00Ca00073811820692005400218FcE1f",
                "0xadA2005600Dec949baf300f4C6120000bDB6eAab");
        approvals = new DepositWalletApprovals(app, relayerProps);
        service = new DepositWalletService(userRepo, relayer, relayerProps, approvals, reader, pending, new ObjectMapper(), event -> {});

        AppUser user = new AppUser();
        user.setWalletAddress(OWNER);
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(relayer.depositWalletAddress(OWNER)).thenReturn(WALLET);
    }

    private void deployed(boolean deployed) {
        when(relayer.isDeployed(WALLET)).thenReturn(Mono.just(deployed));
    }

    private void allApproved() {
        lenient().when(reader.erc20Allowance(anyString(), eq(WALLET), anyString()))
                .thenReturn(ApprovalCalldataBuilder.MAX_UINT256);
        lenient().when(reader.isApprovedForAll(anyString(), eq(WALLET), anyString())).thenReturn(true);
    }

    @Test
    void requiresTenApprovalsIncludingNegRiskAdapterAndRedeemAdapters() {
        assertThat(approvals.all()).hasSize(10);
        assertThat(approvals.all()).extracting(DepositWalletApprovals.Requirement::spender)
                .contains("0xd91E80cF2E7be2e162c6513ceD06f1dD0dA35296",
                        "0xAdA100Db00Ca00073811820692005400218FcE1f", "0xadA2005600Dec949baf300f4C6120000bDB6eAab");
    }

    @Test
    void statusReadyWhenDeployedAndFullyApproved() {
        deployed(true);
        allApproved();
        when(reader.erc20Balance(PUSD, WALLET)).thenReturn(BigInteger.valueOf(4_593_915L));

        var status = service.status(1L);

        assertThat(status.address()).isEqualTo(WALLET);
        assertThat(status.deployed()).isTrue();
        assertThat(status.collateralBalance()).isEqualTo("4593915");
        assertThat(status.approvals()).hasSize(10).allMatch(a -> Boolean.TRUE.equals(a.approved()));
        assertThat(status.readyToTrade()).isTrue();
    }

    @Test
    void statusOfUndeployedWalletSkipsApprovalReads() {
        deployed(false);

        var status = service.status(1L);

        assertThat(status.deployed()).isFalse();
        assertThat(status.readyToTrade()).isFalse();
        assertThat(status.approvals()).allMatch(a -> Boolean.FALSE.equals(a.approved()));
        verify(reader, never()).erc20Allowance(any(), any(), any());
    }

    @Test
    void deployRejectsAlreadyDeployedWallet() {
        deployed(true);

        assertThatThrownBy(() -> service.deploy(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already deployed");
        verify(relayer, never()).createWallet(any());
    }

    @Test
    void deploySubmitsWalletCreate() {
        deployed(false);
        when(relayer.createWallet(OWNER)).thenReturn(Mono.just(new RelayerTransaction("tx-1", null, "STATE_NEW", OWNER, null)));

        var tx = service.deploy(1L);

        assertThat(tx.transactionId()).isEqualTo("tx-1");
    }

    @Test
    void prepareApprovalsIncludesOnlyMissingOrUnknown() {
        deployed(true);
        allApproved();
        // One explicitly missing, one unknown (RPC failure) — both must be (re)approved.
        when(reader.erc20Allowance(PUSD, WALLET, EXCHANGE)).thenReturn(BigInteger.ZERO);
        when(reader.isApprovedForAll(CTF, WALLET, EXCHANGE)).thenReturn(null);
        when(relayer.nonce(OWNER)).thenReturn(Mono.just("4"));

        var prepared = service.prepareApprovals(1L);

        assertThat(prepared.callCount()).isEqualTo(2);
        assertThat(prepared.batchHash()).matches("0x[0-9a-f]{64}");
        ArgumentCaptor<PendingBatchCache.PendingBatch> batch = ArgumentCaptor.forClass(PendingBatchCache.PendingBatch.class);
        verify(pending).put(eq(1L), batch.capture(), eq(Duration.ofMinutes(10)));
        assertThat(batch.getValue().nonce()).isEqualTo("4");
        assertThat(batch.getValue().depositWallet()).isEqualTo(WALLET);
        assertThat(batch.getValue().calls()).extracting(DepositWalletBatch.Call::target).containsExactly(PUSD, CTF);
    }

    @Test
    void prepareApprovalsIsNoOpWhenFullyApproved() {
        deployed(true);
        allApproved();

        var prepared = service.prepareApprovals(1L);

        assertThat(prepared.callCount()).isZero();
        assertThat(prepared.typedData()).isNull();
        verify(relayer, never()).nonce(any());
    }

    @Test
    void prepareApprovalsRequiresDeployment() {
        deployed(false);

        assertThatThrownBy(() -> service.prepareApprovals(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Activate");
    }

    @Test
    void submitBatchRelaysBatchSignedByOwner() {
        var calls = List.of(new DepositWalletBatch.Call(PUSD, "0", "0x095ea7b3"));
        String batchHash = "0x525a65571e5391de0a71368b74d6f70a276121e794bdefbc109da428675c05e2";
        when(pending.get(1L, batchHash))
                .thenReturn(new PendingBatchCache.PendingBatch(batchHash, "APPROVALS", OWNER, WALLET, "7", "1791671000", calls));
        when(relayer.submitBatch(OWNER, WALLET, "7", "1791671000", calls, sign(batchHash, OWNER_KEY)))
                .thenReturn(Mono.just(new RelayerTransaction("tx-2", null, "STATE_NEW", OWNER, null)));

        var tx = service.submitBatch(1L, new DepositWalletDtos.SubmitBatchRequest(batchHash, sign(batchHash, OWNER_KEY)));

        assertThat(tx.transactionId()).isEqualTo("tx-2");
        verify(pending).invalidate(1L, batchHash);
    }

    @Test
    void submitBatchRejectsForeignSignature() {
        String batchHash = "0x525a65571e5391de0a71368b74d6f70a276121e794bdefbc109da428675c05e2";
        when(pending.get(1L, batchHash)).thenReturn(new PendingBatchCache.PendingBatch(
                batchHash, "APPROVALS", OWNER, WALLET, "7", "1791671000", List.of()));
        Credentials stranger = Credentials.create("0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");

        assertThatThrownBy(() -> service.submitBatch(1L,
                new DepositWalletDtos.SubmitBatchRequest(batchHash, sign(batchHash, stranger))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("signed by the wallet");
        verify(relayer, never()).submitBatch(any(), any(), any(), any(), any(), any());
    }

    @Test
    void transactionOfAnotherOwnerIsHidden() {
        when(relayer.getTransaction("tx-9")).thenReturn(Mono.just(
                new RelayerTransaction("tx-9", null, "STATE_MINED", "0x0000000000000000000000000000000000000001", null)));

        assertThatThrownBy(() -> service.transaction(1L, "tx-9"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown relayer transaction");
    }

    /** Raw ECDSA over an EIP-712 digest, {@code r ‖ s ‖ v} — what Privy's signTypedData returns. */
    private static String sign(String digestHex, Credentials key) {
        Sign.SignatureData sd = Sign.signMessage(Numeric.hexStringToByteArray(digestHex), key.getEcKeyPair(), false);
        byte[] out = new byte[65];
        System.arraycopy(sd.getR(), 0, out, 0, 32);
        System.arraycopy(sd.getS(), 0, out, 32, 32);
        out[64] = sd.getV()[0];
        return Numeric.toHexString(out);
    }
}
