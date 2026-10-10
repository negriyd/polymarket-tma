package com.polymarket.tma.redeem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.relayer.DepositWalletBatch;
import com.polymarket.tma.trading.relayer.DepositWalletService;
import com.polymarket.tma.trading.relayer.RelayerProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
class RedeemServiceBatchTest {

    private static final String PUSD = "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB";
    private static final String CTF_ADAPTER = "0xAdA100Db00Ca00073811820692005400218FcE1f";
    private static final String NEG_RISK_ADAPTER = "0xadA2005600Dec949baf300f4C6120000bDB6eAab";
    private static final String CONDITION = "0x" + "ab".repeat(32);

    @Mock private AppProperties props;
    @Mock private AppUserRepository userRepo;
    @Mock private DepositWalletService depositWallets;

    private RedeemService service;

    @BeforeEach
    void setUp() {
        RelayerProperties relayer = new RelayerProperties(null, null, null, null, null, null, null,
                PUSD, null, null, null, CTF_ADAPTER, NEG_RISK_ADAPTER);
        service = new RedeemService(props, userRepo, relayer, depositWallets);
    }

    @SuppressWarnings("unchecked")
    private DepositWalletBatch.Call preparedCall(Boolean negRisk) {
        service.prepareBatch(1L, new RedeemDtos.BatchRequest(CONDITION, negRisk));
        ArgumentCaptor<List<DepositWalletBatch.Call>> calls = ArgumentCaptor.forClass(List.class);
        verify(depositWallets).prepareBatch(eq(1L), eq("REDEEM"), calls.capture());
        assertThat(calls.getValue()).hasSize(1);
        return calls.getValue().get(0);
    }

    @Test
    void standardMarketRedeemsBothOutcomesInPusdViaCtfAdapter() {
        DepositWalletBatch.Call call = preparedCall(false);

        assertThat(call.target()).isEqualTo(CTF_ADAPTER);
        assertThat(call.value()).isEqualTo("0");
        String selector = Numeric.toHexString(
                Hash.sha3("redeemPositions(address,bytes32,bytes32,uint256[])".getBytes())).substring(0, 10);
        assertThat(call.data()).startsWith(selector);
        String data = call.data().substring(10).toLowerCase();
        assertThat(data.substring(0, 64)).endsWith(PUSD.substring(2).toLowerCase());
        assertThat(data.substring(64, 128)).isEqualTo("0".repeat(64));
        assertThat(data.substring(128, 192)).isEqualTo("ab".repeat(32));
        // dynamic array tail: length 2, then index sets 1 and 2
        assertThat(data).endsWith("0".repeat(63) + "2" + "0".repeat(63) + "1" + "0".repeat(63) + "2");
    }

    @Test
    void negRiskMarketUsesNegRiskAdapter() {
        assertThat(preparedCall(true).target()).isEqualTo(NEG_RISK_ADAPTER);
    }
}
