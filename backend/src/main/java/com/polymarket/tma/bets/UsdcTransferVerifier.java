package com.polymarket.tma.bets;

import com.polymarket.tma.config.AppProperties;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/**
 * Verifies an on-chain ERC-20 {@code Transfer} against expected from/to/amount by parsing the
 * transaction receipt logs. Used when a user confirms a bet payment.
 */
@Component
public class UsdcTransferVerifier {

    private static final Logger log = LoggerFactory.getLogger(UsdcTransferVerifier.class);

    /** keccak256("Transfer(address,address,uint256)"). */
    static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163caa73f03281f34041bd71";

    private final AppProperties props;
    private volatile Web3j web3j;

    public UsdcTransferVerifier(AppProperties props) {
        this.props = props;
    }

    public record VerifiedTransfer(String txHash, String from, String to, BigInteger amountMicro) {}

    /**
     * @return a matching transfer if the receipt contains a USDC Transfer log with exact
     *     from/to/amount, or empty if verification fails.
     */
    public Optional<VerifiedTransfer> verify(
            String txHash,
            String expectedFrom,
            String expectedTo,
            BigInteger expectedAmountMicro) {
        if (txHash == null || txHash.isBlank()) {
            return Optional.empty();
        }
        String usdc = props.polygon().usdcAddress();
        if (usdc == null || usdc.isBlank()) {
            log.warn("USDC address not configured — cannot verify transfer");
            return Optional.empty();
        }
        try {
            TransactionReceipt receipt = web3j().ethGetTransactionReceipt(normalizeTxHash(txHash))
                    .send()
                    .getTransactionReceipt()
                    .orElse(null);
            if (receipt == null) {
                log.debug("No receipt yet for tx {}", txHash);
                return Optional.empty();
            }
            if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
                log.debug("Tx {} reverted", txHash);
                return Optional.empty();
            }
            List<Log> logs = receipt.getLogs();
            if (logs == null) {
                return Optional.empty();
            }
            for (Log entry : logs) {
                if (!sameAddress(entry.getAddress(), usdc)) {
                    continue;
                }
                List<String> topics = entry.getTopics();
                if (topics == null || topics.size() < 3) {
                    continue;
                }
                if (!TRANSFER_TOPIC.equalsIgnoreCase(topics.get(0))) {
                    continue;
                }
                String from = topicToAddress(topics.get(1));
                String to = topicToAddress(topics.get(2));
                BigInteger amount = Numeric.toBigInt(entry.getData());
                if (sameAddress(from, expectedFrom)
                        && sameAddress(to, expectedTo)
                        && amount.equals(expectedAmountMicro)) {
                    return Optional.of(new VerifiedTransfer(
                            normalizeTxHash(txHash), from, to, amount));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to verify USDC transfer {}: {}", txHash, e.toString());
            return Optional.empty();
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

    private static String normalizeTxHash(String hash) {
        String h = hash.trim();
        return h.startsWith("0x") ? h : "0x" + h;
    }

    private static String topicToAddress(String topic) {
        if (topic == null || topic.length() < 42) {
            return topic;
        }
        return "0x" + topic.substring(topic.length() - 40).toLowerCase(Locale.ROOT);
    }

    static boolean sameAddress(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }
}
