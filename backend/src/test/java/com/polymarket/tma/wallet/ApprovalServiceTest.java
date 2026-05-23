package com.polymarket.tma.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.config.AppProperties;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    private static final String WALLET = "0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227";
    private static final String EXCHANGE = "0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E";
    private static final String USDC = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174";
    private static final String CTF = "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045";

    @Mock private AppProperties props;
    @Mock private AppUserRepository userRepo;
    @Mock private ApprovalStatusReader reader;

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        when(props.polygon()).thenReturn(new AppProperties.Polygon("", USDC, EXCHANGE, "", CTF));
        AppUser user = new AppUser();
        user.setWalletAddress(WALLET);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        service = new ApprovalService(props, userRepo, reader);
    }

    @Test
    void bothMissingWhenAllowanceZeroAndNotApproved() {
        when(reader.usdcAllowance(WALLET, EXCHANGE)).thenReturn(BigInteger.ZERO);
        when(reader.ctfIsApprovedForAll(WALLET, EXCHANGE)).thenReturn(false);

        ApprovalDtos.ApprovalStatus s = service.status(1L);

        assertThat(s.missing()).hasSize(2);
        assertThat(s.missing().get(0).kind()).isEqualTo("USDC_APPROVE");
        assertThat(s.missing().get(0).to()).isEqualTo(USDC);
        assertThat(s.missing().get(0).chainId()).isEqualTo(137);
        assertThat(s.missing().get(0).value()).isEqualTo("0x0");
        assertThat(s.missing().get(1).kind()).isEqualTo("CTF_SET_APPROVAL_FOR_ALL");
        assertThat(s.missing().get(1).to()).isEqualTo(CTF);
    }

    @Test
    void noneMissingWhenAllowanceAboveThresholdAndApproved() {
        when(reader.usdcAllowance(WALLET, EXCHANGE))
                .thenReturn(BigInteger.valueOf(10_000_000L));
        when(reader.ctfIsApprovedForAll(WALLET, EXCHANGE)).thenReturn(true);

        ApprovalDtos.ApprovalStatus s = service.status(1L);

        assertThat(s.missing()).isEmpty();
        assertThat(s.usdc().approvedForAll()).isTrue();
        assertThat(s.ctf().approvedForAll()).isTrue();
    }

    @Test
    void unknownRpcMeansBothMissingButFlagsNullable() {
        when(reader.usdcAllowance(anyString(), anyString())).thenReturn(null);
        when(reader.ctfIsApprovedForAll(anyString(), anyString())).thenReturn(null);

        ApprovalDtos.ApprovalStatus s = service.status(1L);

        assertThat(s.missing()).hasSize(2);
        assertThat(s.usdc().allowance()).isNull();
        assertThat(s.usdc().approvedForAllKnown()).isNull();
        assertThat(s.ctf().approvedForAllKnown()).isNull();
    }
}
