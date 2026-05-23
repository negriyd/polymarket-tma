package com.polymarket.tma.redeem;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.wallet.ApprovalDtos;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Builds the unsigned tx for redeeming winnings on a settled Polymarket market.
 *
 * <p>The flow mirrors {@link com.polymarket.tma.wallet.ApprovalService}: backend constructs
 * {@code redeemPositions(...)} calldata, the frontend dispatches it as a Polygon tx via Privy
 * {@code useSendTransaction}. We do not RPC-check market resolution here — the upstream
 * {@link com.polymarket.tma.trading.PositionsClient.Position#redeemable()} flag is the source of
 * truth surfaced to the UI; if the market is not actually resolved on-chain the tx will revert and
 * the user keeps their tokens.
 */
@Service
public class RedeemService {

    private static final int POLYGON_CHAIN_ID = 137;

    private final AppProperties props;
    private final AppUserRepository userRepo;

    public RedeemService(AppProperties props, AppUserRepository userRepo) {
        this.props = props;
        this.userRepo = userRepo;
    }

    public RedeemDtos.PrepareResponse prepare(long userId, RedeemDtos.PrepareRequest req) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED",
                    "Wallet address must be set before preparing redemption");
        }

        String ctf = props.polygon().ctfAddress();
        if (ctf == null || ctf.isBlank()) {
            throw ApiException.internal("CTF_NOT_CONFIGURED",
                    "Conditional tokens contract address is not configured");
        }

        BigInteger indexSet = RedeemCalldataBuilder.indexSetFor(req.outcomeIndex());
        String calldata = RedeemCalldataBuilder.redeemPositions(
                props.polygon().usdcAddress(),
                null,
                req.conditionId(),
                List.of(indexSet));

        ApprovalDtos.UnsignedTx tx = new ApprovalDtos.UnsignedTx(
                "CTF_REDEEM_POSITIONS",
                ctf,
                calldata,
                "0x0",
                POLYGON_CHAIN_ID);

        return new RedeemDtos.PrepareResponse(
                req.conditionId(),
                req.outcomeIndex(),
                List.of(indexSet.toString()),
                tx);
    }
}
