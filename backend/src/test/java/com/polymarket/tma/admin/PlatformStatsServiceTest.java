package com.polymarket.tma.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.relayer.RelayerClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformStatsServiceTest {

    private static final String FEE_WALLET = "0x5757DA608Cb6Feaa78dB6388506894F59621eB77";

    @Test
    void onlyTheOwnerOfTheFeeWalletIsOperator() {
        AppUserRepository users = mock(AppUserRepository.class);
        RelayerClient relayer = mock(RelayerClient.class);
        AppProperties props = mock(AppProperties.class);
        when(props.fees()).thenReturn(new AppProperties.Fees(100, FEE_WALLET));

        AppUser owner = new AppUser();
        owner.setWalletAddress("0xOwnerEoa");
        AppUser other = new AppUser();
        other.setWalletAddress("0xOtherEoa");
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(users.findById(4L)).thenReturn(Optional.of(other));
        when(users.findById(5L)).thenReturn(Optional.of(new AppUser()));
        when(relayer.depositWalletAddress("0xOwnerEoa")).thenReturn(FEE_WALLET.toLowerCase());
        when(relayer.depositWalletAddress("0xOtherEoa")).thenReturn("0x0000000000000000000000000000000000000dec");

        var service = new PlatformStatsService(null, users, relayer, props);
        assertThat(service.isOperator(1L)).isTrue();
        assertThat(service.isOperator(4L)).isFalse();
        assertThat(service.isOperator(5L)).as("no wallet yet").isFalse();
    }
}
