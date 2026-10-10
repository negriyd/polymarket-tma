package com.polymarket.tma.trading.relayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.fees.FeeCalldataBuilder;
import com.polymarket.tma.trading.relayer.RelayerClient.RelayerTransaction;
import com.polymarket.tma.wallet.ApprovalStatusReader;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/**
 * Onboards a user's Privy EOA onto CLOB v2 trading through a Polymarket deposit wallet:
 * <ol>
 *     <li>{@link #status} — derived address, deployment, pUSD balance, approvals;</li>
 *     <li>{@link #deploy} — gasless {@code WALLET-CREATE} via the relayer;</li>
 *     <li>{@link #prepareApprovals} / {@link #submitBatch} — the owner signs one EIP-712
 *         {@code Batch} with every missing approval, the relayer executes it gaslessly. The same
 *         batch path carries the platform fee ({@link #prepareCollateralTransfer}) and Claim;</li>
 *     <li>{@link #transaction} — poll relayer progress for either step.</li>
 * </ol>
 * Funding (USDC → pUSD) is not handled here; see Polymarket's bridge {@code POST /deposit}.
 */
@Service
public class DepositWalletService {

    private static final Logger log = LoggerFactory.getLogger(DepositWalletService.class);

    /** Window for the owner to sign in Privy; also bounds how long the relayer may execute it. */
    static final Duration BATCH_DEADLINE = Duration.ofMinutes(10);
    private static final Duration RELAYER_TIMEOUT = Duration.ofSeconds(10);
    /** Same "is approved" threshold as the legacy EOA flow: below 1 pUSD counts as missing. */
    private static final BigInteger MIN_ALLOWANCE = BigInteger.valueOf(1_000_000L);

    private final AppUserRepository userRepo;
    private final RelayerClient relayer;
    private final RelayerProperties relayerProps;
    private final DepositWalletApprovals approvals;
    private final ApprovalStatusReader reader;
    private final PendingBatchCache pending;
    private final ObjectMapper objectMapper;

    public DepositWalletService(AppUserRepository userRepo,
                                RelayerClient relayer,
                                RelayerProperties relayerProps,
                                DepositWalletApprovals approvals,
                                ApprovalStatusReader reader,
                                PendingBatchCache pending,
                                ObjectMapper objectMapper) {
        this.userRepo = userRepo;
        this.relayer = relayer;
        this.relayerProps = relayerProps;
        this.approvals = approvals;
        this.reader = reader;
        this.pending = pending;
        this.objectMapper = objectMapper;
    }

    /** Deposit wallet that trades for {@code owner} (deployed or not). */
    public String depositWalletOf(String owner) {
        return relayer.depositWalletAddress(owner);
    }

    public DepositWalletDtos.Status status(long userId) {
        String owner = owner(userId);
        String wallet = relayer.depositWalletAddress(owner);
        boolean deployed = isDeployed(wallet);

        List<DepositWalletDtos.ApprovalState> states = new ArrayList<>();
        for (DepositWalletApprovals.Requirement r : approvals.all()) {
            states.add(new DepositWalletDtos.ApprovalState(r.kind(), r.token(), r.spender(),
                    deployed ? approved(wallet, r) : Boolean.FALSE));
        }
        BigInteger balance = reader.erc20Balance(relayerProps.collateralAddress(), wallet);
        boolean ready = deployed && states.stream().allMatch(s -> Boolean.TRUE.equals(s.approved()));
        return new DepositWalletDtos.Status(owner, wallet, deployed,
                balance == null ? null : balance.toString(), states, ready);
    }

    public DepositWalletDtos.RelayerTx deploy(long userId) {
        String owner = owner(userId);
        String wallet = relayer.depositWalletAddress(owner);
        if (isDeployed(wallet)) {
            throw ApiException.conflict("DEPOSIT_WALLET_ALREADY_DEPLOYED", "Deposit wallet " + wallet + " is already deployed");
        }
        RelayerTransaction tx = block(relayer.createWallet(owner));
        log.info("Deposit wallet deploy requested user={} owner={} wallet={} tx={}", userId, owner, wallet, tx.transactionID());
        return toDto(tx);
    }

    /** Relayer progress of a transaction this user started; 404 for anyone else's. */
    public DepositWalletDtos.RelayerTx transaction(long userId, String transactionId) {
        String owner = owner(userId);
        RelayerTransaction tx = relayer.getTransaction(transactionId).block(RELAYER_TIMEOUT);
        if (tx == null || tx.from() == null || !tx.from().equalsIgnoreCase(owner)) {
            throw ApiException.notFound("RELAYER_TX_NOT_FOUND", "Unknown relayer transaction " + transactionId);
        }
        return toDto(tx);
    }

    /**
     * Builds the {@code Batch} with every approval not yet confirmed on-chain. Returns
     * {@code callCount == 0} (and no typed data) when the wallet is already fully approved.
     */
    public DepositWalletDtos.PreparedBatch prepareApprovals(long userId) {
        String owner = owner(userId);
        String wallet = relayer.depositWalletAddress(owner);
        requireDeployed(wallet);
        List<DepositWalletBatch.Call> calls = approvals.all().stream()
                .filter(r -> !Boolean.TRUE.equals(approved(wallet, r)))
                .map(DepositWalletApprovals.Requirement::call)
                .toList();
        if (calls.isEmpty()) {
            return new DepositWalletDtos.PreparedBatch(null, null, 0);
        }
        return prepare(userId, owner, wallet, "APPROVALS", calls);
    }

    /** Batch moving {@code amount} pUSD (6 decimals) from the deposit wallet to {@code recipient}. */
    public DepositWalletDtos.PreparedBatch prepareCollateralTransfer(long userId, String purpose,
                                                                     String recipient, BigInteger amount) {
        return prepareBatch(userId, purpose, List.of(new DepositWalletBatch.Call(
                relayerProps.collateralAddress(), "0", FeeCalldataBuilder.erc20Transfer(recipient, amount))));
    }

    /**
     * Batch of server-built {@code calls} for the owner to sign. Only code paths in this backend
     * decide the calls — clients never supply calldata, so our builder credentials can't be used
     * to relay arbitrary transactions.
     */
    public DepositWalletDtos.PreparedBatch prepareBatch(long userId, String purpose, List<DepositWalletBatch.Call> calls) {
        String owner = owner(userId);
        String wallet = relayer.depositWalletAddress(owner);
        requireDeployed(wallet);
        return prepare(userId, owner, wallet, purpose, calls);
    }

    private DepositWalletDtos.PreparedBatch prepare(long userId, String owner, String wallet, String purpose,
                                                    List<DepositWalletBatch.Call> calls) {
        String nonce = block(relayer.nonce(owner));
        String deadline = Long.toString(Instant.now().plus(BATCH_DEADLINE).getEpochSecond());
        Map<String, Object> typedData = DepositWalletBatch.typedData(wallet, nonce, deadline, calls);
        String batchHash = Numeric.toHexString(digest(typedData));

        pending.put(userId, new PendingBatchCache.PendingBatch(batchHash, purpose, owner, wallet, nonce, deadline, calls),
                BATCH_DEADLINE);
        return new DepositWalletDtos.PreparedBatch(batchHash, typedData, calls.size());
    }

    /** Relays a batch previously prepared for this user, once the owner has signed it. */
    public DepositWalletDtos.RelayerTx submitBatch(long userId, DepositWalletDtos.SubmitBatchRequest req) {
        String owner = owner(userId);
        PendingBatchCache.PendingBatch batch = pending.get(userId, req.batchHash());
        if (batch == null || !batch.owner().equalsIgnoreCase(owner)) {
            throw ApiException.notFound("BATCH_NOT_FOUND", "Prepared batch expired or unknown. Re-prepare.");
        }
        String signer = recover(batch.batchHash(), req.signature());
        if (!owner.equalsIgnoreCase(signer)) {
            throw ApiException.badRequest("INVALID_SIGNATURE", "Batch must be signed by the wallet " + owner);
        }
        RelayerTransaction tx = block(relayer.submitBatch(
                owner, batch.depositWallet(), batch.nonce(), batch.deadline(), batch.calls(), req.signature()));
        pending.invalidate(userId, batch.batchHash());
        log.info("{} batch submitted user={} wallet={} calls={} tx={}",
                batch.purpose(), userId, batch.depositWallet(), batch.calls().size(), tx.transactionID());
        return toDto(tx);
    }

    private void requireDeployed(String wallet) {
        if (!isDeployed(wallet)) {
            throw ApiException.conflict("DEPOSIT_WALLET_NOT_DEPLOYED", "Activate the trading wallet first");
        }
    }

    private Boolean approved(String wallet, DepositWalletApprovals.Requirement r) {
        if (r.kind() == DepositWalletApprovals.Kind.ERC20_APPROVE) {
            BigInteger allowance = reader.erc20Allowance(r.token(), wallet, r.spender());
            return allowance == null ? null : allowance.compareTo(MIN_ALLOWANCE) >= 0;
        }
        return reader.isApprovedForAll(r.token(), wallet, r.spender());
    }

    private boolean isDeployed(String wallet) {
        return Boolean.TRUE.equals(block(relayer.isDeployed(wallet)));
    }

    private String owner(long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED", "Wallet address must be set first");
        }
        return user.getWalletAddress();
    }

    private byte[] digest(Map<String, Object> typedData) {
        try {
            return new StructuredDataEncoder(objectMapper.writeValueAsString(typedData)).hashStructuredData();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize batch typed data", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash batch typed data", e);
        }
    }

    /** Address that produced a 65-byte {@code r ‖ s ‖ v} signature over {@code digestHex}; null if malformed. */
    static String recover(String digestHex, String signatureHex) {
        try {
            byte[] sig = Numeric.hexStringToByteArray(signatureHex);
            if (sig.length != 65) {
                return null;
            }
            byte v = sig[64] < 27 ? (byte) (sig[64] + 27) : sig[64];
            var data = new Sign.SignatureData(v, Arrays.copyOfRange(sig, 0, 32), Arrays.copyOfRange(sig, 32, 64));
            return "0x" + Keys.getAddress(Sign.signedMessageHashToKey(Numeric.hexStringToByteArray(digestHex), data));
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> T block(reactor.core.publisher.Mono<T> mono) {
        T value = mono.block(RELAYER_TIMEOUT);
        if (value == null) {
            throw ApiException.upstream("RELAYER_EMPTY_RESPONSE", "Polymarket relayer returned an empty response");
        }
        return value;
    }

    private static DepositWalletDtos.RelayerTx toDto(RelayerTransaction tx) {
        return new DepositWalletDtos.RelayerTx(tx.transactionID(), tx.state(), tx.transactionHash());
    }
}
