package com.polymarket.tma.trading.bridge;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.relayer.DepositWalletDtos;
import com.polymarket.tma.trading.relayer.DepositWalletService;
import com.polymarket.tma.trading.relayer.RelayerProperties;
import com.polymarket.tma.wallet.ApprovalStatusReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Withdraws pUSD from the deposit wallet: either directly as pUSD on Polygon, or through the
 * Polymarket bridge, which unwraps and swaps it into a stablecoin on another network. Either way
 * the money leaves via a gasless deposit-wallet batch the owner signs.
 */
@Service
public class WithdrawService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawService.class);

    static final String POLYGON_CHAIN_ID = "137";
    private static final Duration BRIDGE_TIMEOUT = Duration.ofSeconds(10);
    private static final BigDecimal MICRO = BigDecimal.valueOf(1_000_000L);

    private static final String EVM = "^0x[0-9a-fA-F]{40}$";
    private static final String SOLANA = "^[1-9A-HJ-NP-Za-km-z]{32,44}$";
    private static final String TRON = "^T[1-9A-HJ-NP-Za-km-z]{33}$";

    private final AppUserRepository userRepo;
    private final DepositWalletService depositWallets;
    private final RelayerProperties relayerProps;
    private final FundingService funding;
    private final BridgeClient bridge;
    private final ApprovalStatusReader reader;

    public WithdrawService(AppUserRepository userRepo,
                           DepositWalletService depositWallets,
                           RelayerProperties relayerProps,
                           FundingService funding,
                           BridgeClient bridge,
                           ApprovalStatusReader reader) {
        this.userRepo = userRepo;
        this.depositWallets = depositWallets;
        this.relayerProps = relayerProps;
        this.funding = funding;
        this.bridge = bridge;
        this.reader = reader;
    }

    public WithdrawDtos.Options options(long userId) {
        String wallet = depositWallets.depositWalletOf(owner(userId));
        BigInteger balance = reader.erc20Balance(relayerProps.collateralAddress(), wallet);
        return new WithdrawDtos.Options(wallet, balance == null ? null : balance.toString(), destinations());
    }

    public WithdrawDtos.Prepared prepare(long userId, WithdrawDtos.PrepareRequest req) {
        String wallet = depositWallets.depositWalletOf(owner(userId));
        WithdrawDtos.Destination dest = destinations().stream()
                .filter(d -> d.chainId().equals(req.toChainId()) && d.tokenAddress().equalsIgnoreCase(req.toTokenAddress()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("WITHDRAW_DESTINATION_UNSUPPORTED",
                        "Unsupported destination " + req.toChainId() + "/" + req.toTokenAddress()));
        if (!req.recipient().matches(recipientPattern(dest.chainName()))) {
            throw ApiException.badRequest("WITHDRAW_RECIPIENT_INVALID",
                    "Recipient is not a valid " + dest.chainName() + " address");
        }
        BigInteger amount = new BigInteger(req.amountBaseUnit());
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("WITHDRAW_AMOUNT_INVALID", "Amount must be positive");
        }
        if (dest.minUsd() != null && new BigDecimal(amount).divide(MICRO).compareTo(BigDecimal.valueOf(dest.minUsd())) < 0) {
            throw ApiException.badRequest("WITHDRAW_BELOW_MINIMUM",
                    "Minimum withdrawal to " + dest.chainName() + " is $" + dest.minUsd());
        }
        BigInteger balance = reader.erc20Balance(relayerProps.collateralAddress(), wallet);
        if (balance != null && amount.compareTo(balance) > 0) {
            throw ApiException.badRequest("WITHDRAW_INSUFFICIENT_BALANCE", "Amount exceeds the pUSD balance");
        }

        if (dest.route() == WithdrawDtos.Route.DIRECT) {
            DepositWalletDtos.PreparedBatch batch =
                    depositWallets.prepareCollateralTransfer(userId, "WITHDRAW", req.recipient(), amount);
            return new WithdrawDtos.Prepared(batch, WithdrawDtos.Route.DIRECT, req.recipient(), req.amountBaseUnit(), null);
        }

        BridgeClient.DepositAddresses addresses = bridge
                .withdrawalAddresses(wallet, dest.chainId(), dest.tokenAddress(), req.recipient())
                .block(BRIDGE_TIMEOUT);
        if (addresses == null || addresses.evm() == null || !addresses.evm().matches(EVM)) {
            throw ApiException.upstream("BRIDGE_WITHDRAW_NO_ADDRESS", "Polymarket bridge returned no Polygon withdrawal address");
        }
        Optional<BridgeClient.Quote> quote = quote(dest, req, amount);
        DepositWalletDtos.PreparedBatch batch =
                depositWallets.prepareCollateralTransfer(userId, "WITHDRAW", addresses.evm(), amount);
        log.info("Withdrawal prepared user={} wallet={} amount={} to {}:{} via {}",
                userId, wallet, amount, dest.chainName(), dest.symbol(), addresses.evm());
        return new WithdrawDtos.Prepared(batch, WithdrawDtos.Route.BRIDGE, addresses.evm(),
                quote.map(BridgeClient.Quote::estToTokenBaseUnit).orElse(null),
                quote.map(BridgeClient.Quote::estCheckoutTimeMs).orElse(null));
    }

    private Optional<BridgeClient.Quote> quote(WithdrawDtos.Destination dest, WithdrawDtos.PrepareRequest req, BigInteger amount) {
        try {
            return Optional.ofNullable(bridge.quote(POLYGON_CHAIN_ID, relayerProps.collateralAddress(), amount.toString(),
                    dest.chainId(), dest.tokenAddress(), req.recipient()).block(BRIDGE_TIMEOUT));
        } catch (RuntimeException e) {
            // A quote is informational; the withdrawal itself does not depend on it.
            log.warn("Bridge quote failed for {}:{}: {}", dest.chainName(), dest.symbol(), e.toString());
            return Optional.empty();
        }
    }

    /** pUSD on Polygon first (no swap), then every bridged stablecoin we also accept for deposits. */
    List<WithdrawDtos.Destination> destinations() {
        List<WithdrawDtos.Destination> out = new ArrayList<>();
        out.add(new WithdrawDtos.Destination(WithdrawDtos.Route.DIRECT, POLYGON_CHAIN_ID, "Polygon", "pUSD",
                relayerProps.collateralAddress(), 6, null));
        for (BridgeClient.SupportedAsset a : funding.stablecoins()) {
            out.add(new WithdrawDtos.Destination(WithdrawDtos.Route.BRIDGE, a.chainId(), a.chainName(),
                    a.token().symbol(), a.token().address(), a.token().decimals(), a.minCheckoutUsd()));
        }
        return out;
    }

    static String recipientPattern(String chainName) {
        return switch (chainName) {
            case "Solana" -> SOLANA;
            case "Tron" -> TRON;
            default -> EVM;
        };
    }

    private String owner(long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED", "Wallet address must be set first");
        }
        return user.getWalletAddress();
    }
}
