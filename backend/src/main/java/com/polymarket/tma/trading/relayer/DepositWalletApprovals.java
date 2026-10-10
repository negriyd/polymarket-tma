package com.polymarket.tma.trading.relayer;

import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.wallet.ApprovalCalldataBuilder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The approvals a deposit wallet needs before CLOB v2 accepts its orders. Same set the probe
 * verified against CLOB's {@code /balance-allowance} on 2026-10-11:
 * <ul>
 *     <li>pUSD {@code approve(max)} → standard exchange, NegRisk exchange, NegRiskAdapter, exchange v3;</li>
 *     <li>CTF {@code setApprovalForAll} → standard exchange, NegRisk exchange, NegRiskAdapter;</li>
 *     <li>PositionManager {@code setApprovalForAll} → exchange v3;</li>
 *     <li>CTF {@code setApprovalForAll} → CtfCollateralAdapter, NegRiskCtfCollateralAdapter (Claim / redeem).</li>
 * </ul>
 */
@Component
public class DepositWalletApprovals {

    public enum Kind { ERC20_APPROVE, ERC1155_SET_APPROVAL_FOR_ALL }

    public record Requirement(Kind kind, String token, String spender) {
        public DepositWalletBatch.Call call() {
            String data = kind == Kind.ERC20_APPROVE
                    ? ApprovalCalldataBuilder.erc20Approve(spender, ApprovalCalldataBuilder.MAX_UINT256)
                    : ApprovalCalldataBuilder.erc1155SetApprovalForAll(spender, true);
            return new DepositWalletBatch.Call(token, "0", data);
        }
    }

    private final List<Requirement> requirements;

    public DepositWalletApprovals(AppProperties app, RelayerProperties relayer) {
        String exchange = app.polygon().ctfExchangeAddress();
        String negRiskExchange = app.polygon().negRiskCtfExchangeAddress();
        String ctf = app.polygon().ctfAddress();
        String pusd = relayer.collateralAddress();
        this.requirements = List.of(
                new Requirement(Kind.ERC20_APPROVE, pusd, exchange),
                new Requirement(Kind.ERC20_APPROVE, pusd, negRiskExchange),
                new Requirement(Kind.ERC20_APPROVE, pusd, relayer.negRiskAdapterAddress()),
                new Requirement(Kind.ERC20_APPROVE, pusd, relayer.exchangeV3Address()),
                new Requirement(Kind.ERC1155_SET_APPROVAL_FOR_ALL, ctf, exchange),
                new Requirement(Kind.ERC1155_SET_APPROVAL_FOR_ALL, ctf, negRiskExchange),
                new Requirement(Kind.ERC1155_SET_APPROVAL_FOR_ALL, ctf, relayer.negRiskAdapterAddress()),
                new Requirement(Kind.ERC1155_SET_APPROVAL_FOR_ALL, relayer.positionManagerAddress(),
                        relayer.exchangeV3Address()),
                new Requirement(Kind.ERC1155_SET_APPROVAL_FOR_ALL, ctf, relayer.ctfCollateralAdapterAddress()),
                new Requirement(Kind.ERC1155_SET_APPROVAL_FOR_ALL, ctf, relayer.negRiskCtfCollateralAdapterAddress()));
    }

    public List<Requirement> all() {
        return requirements;
    }
}
