/** Golden vectors for the Java relayer client (backend RelayerVectorsTest). Public test data only. */
import { hashTypedData, type Address, type Hex } from "viem";
import { privateKeyToAccount } from "viem/accounts";
import { deriveBeaconDepositWallet, deriveUupsDepositWallet } from "@polymarket/builder-relayer-client/dist/builder/derive.js";
import { buildHmacSignature } from "@polymarket/builder-signing-sdk/dist/signing/hmac.js";

const FACTORY: Address = "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07";
const BEACON: Address = "0x7A18EDfe055488A3128f01F563e5B479D92ffc3a";
const IMPL: Address = "0x58CA52ebe0DadfdF531Cde7062e76746de4Db1eB";
// Hardhat account #0 — publicly known key, never funded on Polygon.
const TEST_KEY: Hex = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

const owners: Address[] = ["0x415aE735eB964da56C11D0a53F0f082b85FcbEA5", privateKeyToAccount(TEST_KEY).address];
const derive = owners.map((o) => ({
    owner: o,
    beacon: deriveBeaconDepositWallet(o, FACTORY, BEACON),
    uups: deriveUupsDepositWallet(o, FACTORY, IMPL),
}));

const wallet = derive[1].beacon as Address;
const typedData = {
    domain: { name: "DepositWallet", version: "1", chainId: 137, verifyingContract: wallet },
    types: {
        Call: [{ name: "target", type: "address" }, { name: "value", type: "uint256" }, { name: "data", type: "bytes" }],
        Batch: [{ name: "wallet", type: "address" }, { name: "nonce", type: "uint256" }, { name: "deadline", type: "uint256" }, { name: "calls", type: "Call[]" }],
    },
    primaryType: "Batch" as const,
    message: {
        wallet,
        nonce: 7n,
        deadline: 1791671000n,
        calls: [
            { target: "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB" as Address, value: 0n,
              data: "0x095ea7b3000000000000000000000000e111180000d2663c0091e4f400237545b87b996bffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" as Hex },
            { target: "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045" as Address, value: 0n,
              data: "0xa22cb465000000000000000000000000e111180000d2663c0091e4f400237545b87b996b0000000000000000000000000000000000000000000000000000000000000001" as Hex },
        ],
    },
};
const batchDigest = hashTypedData(typedData);
const batchSignature = await privateKeyToAccount(TEST_KEY).signTypedData(typedData);

const hmacSecret = "dGVzdC1zZWNyZXQtZm9yLXZlY3RvcnMtb25seS0xMjM0NTY3OA=="; // base64("test-secret-for-vectors-only-12345678")
const hmacBody = '{"type":"WALLET-CREATE","from":"0x415aE735eB964da56C11D0a53F0f082b85FcbEA5","to":"0x00000000000Fb5C9ADea0298D729A0CB3823Cc07"}';
const hmac = buildHmacSignature(hmacSecret, 1791671000, "POST", "/submit", hmacBody);

console.log(JSON.stringify({ derive, batch: { wallet, nonce: "7", deadline: "1791671000", digest: batchDigest, signer: privateKeyToAccount(TEST_KEY).address, signature: batchSignature }, hmac: { secret: hmacSecret, timestamp: 1791671000, method: "POST", path: "/submit", body: hmacBody, signature: hmac } }, null, 2));
