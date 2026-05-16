package com.polymarket.tma.wallet;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Builds approval status + unsigned tx list for the trading flow.
 *
 * <p>Threshold: USDC allowance &lt; 1 USDC (1e6) is considered missing — production wants effectively
 * unlimited allowance once and only top-ups on rotation. CTF is binary ({@code isApprovedForAll}).
 */
@Service
public class ApprovalService {

    /** 1 USDC; anything below this means "user has not approved yet" for the trading UX. */
    static final BigInteger USDC_MIN_ALLOWANCE = BigInteger.valueOf(1_000_000L);

    private static final int POLYGON_CHAIN_ID = 137;

    private final AppProperties props;
    private final AppUserRepository userRepo;
    private final ApprovalStatusReader reader;

    public ApprovalService(AppProperties props,
                           AppUserRepository userRepo,
                           ApprovalStatusReader reader) {
        this.props = props;
        this.userRepo = userRepo;
        this.reader = reader;
    }

    public ApprovalDtos.ApprovalStatus status(long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED",
                    "Wallet address must be set before requesting approval status");
        }

        String wallet = user.getWalletAddress();
        String spender = props.polygon().ctfExchangeAddress();
        String usdcAddress = props.polygon().usdcAddress();
        String ctfAddress = props.polygon().ctfAddress();

        BigInteger allowance = reader.usdcAllowance(wallet, spender);
        Boolean isApproved = reader.ctfIsApprovedForAll(wallet, spender);

        ApprovalDtos.AllowanceState usdcState = new ApprovalDtos.AllowanceState(
                spender,
                allowance != null ? allowance.toString() : null,
                Boolean.TRUE.equals(isApproved),
                isApproved);

        ApprovalDtos.AllowanceState ctfState = new ApprovalDtos.AllowanceState(
                spender,
                null,
                Boolean.TRUE.equals(isApproved),
                isApproved);

        List<ApprovalDtos.UnsignedTx> missing = new ArrayList<>(2);

        boolean usdcNeeded = allowance == null || allowance.compareTo(USDC_MIN_ALLOWANCE) < 0;
        if (usdcNeeded) {
            missing.add(new ApprovalDtos.UnsignedTx(
                    "USDC_APPROVE",
                    usdcAddress,
                    ApprovalCalldataBuilder.erc20Approve(spender, ApprovalCalldataBuilder.MAX_UINT256),
                    "0x0",
                    POLYGON_CHAIN_ID));
        }
        boolean ctfNeeded = isApproved == null || !isApproved;
        if (ctfNeeded) {
            missing.add(new ApprovalDtos.UnsignedTx(
                    "CTF_SET_APPROVAL_FOR_ALL",
                    ctfAddress,
                    ApprovalCalldataBuilder.erc1155SetApprovalForAll(spender, true),
                    "0x0",
                    POLYGON_CHAIN_ID));
        }

        return new ApprovalDtos.ApprovalStatus(wallet, spender, usdcState, ctfState, missing);
    }
}
