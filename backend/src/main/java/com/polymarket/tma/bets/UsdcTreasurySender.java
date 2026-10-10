package com.polymarket.tma.bets;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.fees.FeeCalldataBuilder;
import java.io.IOException;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/** Broadcasts ERC-20 USDC transfers signed by the treasury hot wallet. */
@Component
public class UsdcTreasurySender {

    private static final Logger log = LoggerFactory.getLogger(UsdcTreasurySender.class);
    private static final int POLYGON_CHAIN_ID = 137;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(120_000L);

    private final AppProperties props;
    private volatile Web3j web3j;

    public UsdcTreasurySender(AppProperties props) {
        this.props = props;
    }

    public boolean canSend() {
        AppProperties.Bets cfg = props.bets();
        return cfg != null
                && cfg.treasuryPrivateKey() != null
                && !cfg.treasuryPrivateKey().isBlank();
    }

    public String sendUsdc(String recipient, BigInteger amountMicro) {
        AppProperties.Bets cfg = props.bets();
        if (cfg == null || cfg.treasuryPrivateKey() == null || cfg.treasuryPrivateKey().isBlank()) {
            throw ApiException.badRequest("WITHDRAW_SEND_DISABLED",
                    "Treasury private key not configured (BETS_TREASURY_PRIVATE_KEY)");
        }
        String usdc = props.polygon().usdcAddress();
        if (usdc == null || usdc.isBlank()) {
            throw ApiException.badRequest("USDC_NOT_CONFIGURED", "USDC contract address missing");
        }

        Credentials creds = Credentials.create(stripHexPrefix(cfg.treasuryPrivateKey()));
        String treasury = cfg.treasuryAddress();
        if (treasury != null && !treasury.isBlank()
                && !UsdcTransferVerifier.sameAddress(creds.getAddress(), treasury)) {
            throw ApiException.badRequest("TREASURY_KEY_MISMATCH",
                    "Treasury private key address does not match BETS_TREASURY_ADDRESS");
        }

        try {
            Web3j w3 = web3j();
            BigInteger nonce = w3.ethGetTransactionCount(creds.getAddress(), org.web3j.protocol.core.DefaultBlockParameterName.PENDING)
                    .send()
                    .getTransactionCount();
            BigInteger gasPrice = w3.ethGasPrice().send().getGasPrice();
            String data = FeeCalldataBuilder.erc20Transfer(recipient, amountMicro);
            RawTransaction raw = RawTransaction.createTransaction(
                    nonce, gasPrice, GAS_LIMIT, usdc, BigInteger.ZERO, data);
            byte[] signed = TransactionEncoder.signMessage(raw, POLYGON_CHAIN_ID, creds);
            EthSendTransaction resp = w3.ethSendRawTransaction(Numeric.toHexString(signed)).send();
            if (resp.hasError()) {
                String msg = resp.getError() == null ? "unknown RPC error" : resp.getError().getMessage();
                throw ApiException.badRequest("WITHDRAW_TX_FAILED", "Failed to send USDC: " + msg);
            }
            String hash = resp.getTransactionHash();
            log.info("Sent USDC withdraw tx {} to {} amountMicro={}", hash, recipient, amountMicro);
            return hash;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            log.warn("USDC send failed: {}", e.toString());
            throw ApiException.badRequest("WITHDRAW_TX_FAILED", "RPC error while sending USDC");
        }
    }

    private Web3j web3j() {
        Web3j w = this.web3j;
        if (w == null) {
            synchronized (this) {
                if (this.web3j == null) {
                    this.web3j = Web3j.build(new HttpService(props.polygon().rpcUrl()));
                }
                w = this.web3j;
            }
        }
        return w;
    }

    private static String stripHexPrefix(String key) {
        String k = key.trim();
        if (k.startsWith("0x") || k.startsWith("0X")) {
            return k.substring(2);
        }
        return k;
    }
}
