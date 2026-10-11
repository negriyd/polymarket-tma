package com.polymarket.tma.trading.relayer;

/** Published after the relayer accepted a deposit-wallet batch signed by its owner. */
public record DepositWalletBatchSubmitted(long userId, String purpose, String batchHash, String relayerTxId) {
}
