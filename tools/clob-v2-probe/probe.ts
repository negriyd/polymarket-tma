/**
 * Polymarket CLOB v2 deposit-wallet probe.
 *
 * Goal: prove end-to-end (with the official TS SDKs) that an EOA can trade through a
 * Deposit Wallet + POLY_1271 signatures, and dump the exact signed order so the Java
 * OrderBuilder can be checked byte-for-byte against it.
 *
 * Steps (run in order, each one is idempotent-ish):
 *   info                       addresses, deployment state, balances, allowances (read-only)
 *   deploy                     WALLET-CREATE via relayer (needs builder creds, gasless)
 *   wrap <usdc>                deposit wallet wraps its own USDC.e into pUSD (gasless batch)
 *   fund <usdc>                EOA wraps its USDC.e into pUSD for the deposit wallet (EOA pays POL gas)
 *   send <to> <pusd|all>       transfer pUSD from the deposit wallet to any Polygon address (gasless batch)
 *   approve                    8 trading approvals from the deposit wallet (gasless batch)
 *   auth                       L1 -> API key (POLY_ADDRESS = EOA), saved to .creds.json
 *   balance                    CLOB view of pUSD balance/allowance for signature_type=3
 *   verify <tokenId>           sign an order locally (NOT posted) and check it via ERC-1271 isValidSignature
 *   order <tokenId> [price] [size] [--keep]
 *                              post-only GTC BUY far below market, dump it, then cancel
 */
import "dotenv/config";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import {
    type Address,
    type Hex,
    createPublicClient,
    createWalletClient,
    encodeFunctionData,
    erc20Abi,
    formatUnits,
    hashTypedData,
    http,
    maxUint256,
    parseAbi,
    parseUnits,
} from "viem";
import { privateKeyToAccount } from "viem/accounts";
import { polygon } from "viem/chains";
import { type DepositWalletCall, RelayClient } from "@polymarket/builder-relayer-client";
import { BuilderConfig } from "@polymarket/builder-signing-sdk";
import {
    type ApiKeyCreds,
    AssetType,
    Chain,
    ClobClient,
    OrderType,
    Side,
    SignatureTypeV2,
} from "@polymarket/clob-client-v2";

const CLOB_HOST = process.env.CLOB_HOST ?? "https://clob.polymarket.com";
const RELAYER_URL = process.env.RELAYER_URL ?? "https://relayer-v2.polymarket.com";
const RPC_URL = process.env.RPC_URL ?? "https://polygon-rpc.com";
const CREDS_FILE = ".creds.json";
const OUT_DIR = "out";

const USDC_E: Address = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174";
const PUSD: Address = "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB";
const ONRAMP: Address = "0x93070a847efEf7F70739046A929D47a521F5B8ee";
const CTF: Address = "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045";
const POSITION_MANAGER: Address = "0x006F54F7f9A22e0000CC2AB60031000000ae9fEF";
const EXCHANGE_V2: Address = "0xE111180000d2663C0091e4f400237545B87B996B";
const NEG_RISK_EXCHANGE_V2: Address = "0xe2222d279d744050d28e00520010520000310F59";
const EXCHANGE_V3: Address = "0xe3333700cA9d93003F00f0F71f8515005F6c00Aa";
const NEG_RISK_ADAPTER: Address = "0xd91E80cF2E7be2e162c6513ceD06f1dD0dA35296";

const erc1155Abi = parseAbi([
    "function setApprovalForAll(address operator, bool approved)",
    "function isApprovedForAll(address owner, address operator) view returns (bool)",
]);
const erc1271Abi = parseAbi(["function isValidSignature(bytes32 hash, bytes signature) view returns (bytes4)"]);
const ORDER_TYPES = [
    { name: "salt", type: "uint256" }, { name: "maker", type: "address" }, { name: "signer", type: "address" },
    { name: "tokenId", type: "uint256" }, { name: "makerAmount", type: "uint256" }, { name: "takerAmount", type: "uint256" },
    { name: "side", type: "uint8" }, { name: "signatureType", type: "uint8" }, { name: "timestamp", type: "uint256" },
    { name: "metadata", type: "bytes32" }, { name: "builder", type: "bytes32" },
] as const;
const onrampAbi =parseAbi(["function wrap(address underlyingToken, address recipient, uint256 amount)"]);

function requireEnv(name: string): string {
    const v = process.env[name];
    if (!v) throw new Error(`Missing env ${name} (see .env.example)`);
    return v;
}

const account = privateKeyToAccount(requireEnv("PRIVATE_KEY") as Hex);
const wallet = createWalletClient({ account, chain: polygon, transport: http(RPC_URL) });
const publicClient = createPublicClient({ chain: polygon, transport: http(RPC_URL) });

function builderConfig(): BuilderConfig | undefined {
    const key = process.env.BUILDER_API_KEY;
    const secret = process.env.BUILDER_SECRET;
    const passphrase = process.env.BUILDER_PASSPHRASE;
    if (!key || !secret || !passphrase) return undefined;
    return new BuilderConfig({ localBuilderCreds: { key, secret, passphrase } });
}

function relay(): RelayClient {
    return new RelayClient(RELAYER_URL, Chain.POLYGON, wallet, builderConfig());
}

function requireBuilder(): void {
    if (!builderConfig()) {
        throw new Error("Builder creds required: BUILDER_API_KEY / BUILDER_SECRET / BUILDER_PASSPHRASE "
            + "(polymarket.com -> Settings -> Builders)");
    }
}

async function depositWallet(): Promise<Address> {
    return (await relay().deriveDepositWalletAddress()) as Address;
}

function loadCreds(): ApiKeyCreds {
    if (!existsSync(CREDS_FILE)) throw new Error(`No ${CREDS_FILE}; run "auth" first`);
    return JSON.parse(readFileSync(CREDS_FILE, "utf8"));
}

async function clob(withCreds: boolean): Promise<ClobClient> {
    return new ClobClient({
        host: CLOB_HOST,
        chain: Chain.POLYGON,
        signer: wallet,
        creds: withCreds ? loadCreds() : undefined,
        signatureType: SignatureTypeV2.POLY_1271,
        funderAddress: await depositWallet(),
    });
}

async function erc20Balance(token: Address, owner: Address): Promise<string> {
    const raw = await publicClient.readContract({ address: token, abi: erc20Abi, functionName: "balanceOf", args: [owner] });
    return formatUnits(raw, 6);
}

async function runBatch(calls: DepositWalletCall[], label: string): Promise<void> {
    requireBuilder();
    const rc = relay();
    const dw = await depositWallet();
    if (!(await rc.getDeployed(dw, "WALLET"))) throw new Error(`Deposit wallet ${dw} is not deployed; run "deploy"`);
    const deadline = Math.floor(Date.now() / 1000 + 240).toString();
    console.log(`${label}: submitting batch of ${calls.length} call(s) from ${dw}...`);
    const resp = await rc.executeDepositWalletBatch(calls, dw, deadline);
    const result = await resp.wait();
    console.log(`${label}: done`, result?.transactionHash ?? result);
}

const commands: Record<string, (args: string[]) => Promise<void>> = {
    async info() {
        const rc = relay();
        const dw = await depositWallet();
        const deployed = await rc.getDeployed(dw, "WALLET");
        console.log("EOA (signer):        ", account.address);
        console.log("Deposit wallet:      ", dw, deployed ? "(deployed)" : "(NOT deployed)");
        console.log("Builder creds:       ", builderConfig() ? "present" : "missing");
        console.log("CLOB order version:  ", await (await clob(false)).getVersion());
        const pol = formatUnits(await publicClient.getBalance({ address: account.address }), 18);
        console.log(`EOA   POL=${pol} USDC.e=${await erc20Balance(USDC_E, account.address)} pUSD=${await erc20Balance(PUSD, account.address)}`);
        console.log(`DW    USDC.e=${await erc20Balance(USDC_E, dw)} pUSD=${await erc20Balance(PUSD, dw)}`);
        for (const spender of [EXCHANGE_V2, NEG_RISK_EXCHANGE_V2, NEG_RISK_ADAPTER, EXCHANGE_V3]) {
            const a = await publicClient.readContract({ address: PUSD, abi: erc20Abi, functionName: "allowance", args: [dw, spender] });
            console.log(`  pUSD allowance -> ${spender}: ${a === maxUint256 ? "max" : formatUnits(a, 6)}`);
        }
        for (const [token, op] of [[CTF, EXCHANGE_V2], [CTF, NEG_RISK_EXCHANGE_V2], [CTF, NEG_RISK_ADAPTER], [POSITION_MANAGER, EXCHANGE_V3]] as const) {
            const ok = await publicClient.readContract({ address: token, abi: erc1155Abi, functionName: "isApprovedForAll", args: [dw, op] });
            console.log(`  ${token === CTF ? "CTF" : "PositionManager"} approvedForAll -> ${op}: ${ok}`);
        }
        console.log("API creds file:      ", existsSync(CREDS_FILE) ? CREDS_FILE : "none");
    },

    async deploy() {
        requireBuilder();
        const rc = relay();
        const dw = await depositWallet();
        if (await rc.getDeployed(dw, "WALLET")) {
            console.log(`Already deployed: ${dw}`);
            return;
        }
        console.log(`Deploying deposit wallet ${dw}...`);
        const result = await (await rc.deployDepositWallet()).wait();
        console.log("Deployed:", result?.transactionHash ?? result);
    },

    async wrap([amount]) {
        if (!amount) throw new Error("usage: wrap <usdc>");
        const dw = await depositWallet();
        const raw = parseUnits(amount, 6);
        await runBatch([
            { target: USDC_E, value: "0", data: encodeFunctionData({ abi: erc20Abi, functionName: "approve", args: [ONRAMP, raw] }) },
            { target: ONRAMP, value: "0", data: encodeFunctionData({ abi: onrampAbi, functionName: "wrap", args: [USDC_E, dw, raw] }) },
        ], "wrap");
    },

    async send([to, amount]) {
        if (!to || !/^0x[0-9a-fA-F]{40}$/.test(to) || !amount) throw new Error("usage: send <0xRecipient> <pusd|all>");
        const dw = await depositWallet();
        const balance = await publicClient.readContract({ address: PUSD, abi: erc20Abi, functionName: "balanceOf", args: [dw] });
        const raw = amount === "all" ? balance : parseUnits(amount, 6);
        if (raw <= 0n || raw > balance) throw new Error(`amount ${formatUnits(raw, 6)} vs balance ${formatUnits(balance, 6)} pUSD`);
        console.log(`Sending ${formatUnits(raw, 6)} pUSD from ${dw} to ${to} (Polygon)`);
        await runBatch([
            { target: PUSD, value: "0", data: encodeFunctionData({ abi: erc20Abi, functionName: "transfer", args: [to as Address, raw] }) },
        ], "send");
        console.log(`Recipient pUSD now: ${await erc20Balance(PUSD, to as Address)}`);
    },

    async fund([amount]) {
        if (!amount) throw new Error("usage: fund <usdc>");
        const dw = await depositWallet();
        const raw = parseUnits(amount, 6);
        console.log(`EOA approves ${amount} USDC.e to onramp...`);
        let hash = await wallet.writeContract({ address: USDC_E, abi: erc20Abi, functionName: "approve", args: [ONRAMP, raw] });
        await publicClient.waitForTransactionReceipt({ hash });
        console.log(`EOA wraps ${amount} USDC.e -> pUSD for ${dw}...`);
        hash = await wallet.writeContract({ address: ONRAMP, abi: onrampAbi, functionName: "wrap", args: [USDC_E, dw, raw] });
        await publicClient.waitForTransactionReceipt({ hash });
        console.log("Done:", hash);
    },

    async approve() {
        const approve = (spender: Address): DepositWalletCall => ({
            target: PUSD, value: "0",
            data: encodeFunctionData({ abi: erc20Abi, functionName: "approve", args: [spender, maxUint256] }),
        });
        const setAll = (token: Address, operator: Address): DepositWalletCall => ({
            target: token, value: "0",
            data: encodeFunctionData({ abi: erc1155Abi, functionName: "setApprovalForAll", args: [operator, true] }),
        });
        await runBatch([
            approve(EXCHANGE_V2),
            approve(NEG_RISK_EXCHANGE_V2),
            approve(NEG_RISK_ADAPTER),
            approve(EXCHANGE_V3),
            setAll(CTF, EXCHANGE_V2),
            setAll(CTF, NEG_RISK_EXCHANGE_V2),
            setAll(CTF, NEG_RISK_ADAPTER),
            setAll(POSITION_MANAGER, EXCHANGE_V3),
        ], "approve");
    },

    async auth() {
        const creds = await (await clob(false)).createOrDeriveApiKey();
        if (!creds?.key) throw new Error(`createOrDeriveApiKey failed: ${JSON.stringify(creds)}`);
        writeFileSync(CREDS_FILE, JSON.stringify(creds, null, 2));
        console.log(`API key ${creds.key} saved to ${CREDS_FILE} (bound to EOA ${account.address})`);
    },

    async balance() {
        const c = await clob(true);
        await c.updateBalanceAllowance({ asset_type: AssetType.COLLATERAL });
        console.log(JSON.stringify(await c.getBalanceAllowance({ asset_type: AssetType.COLLATERAL }), null, 2));
    },

    async verify([tokenID]) {
        if (!tokenID) throw new Error("usage: verify <tokenId>");
        const c = await clob(false);
        const book = await c.getOrderBook(tokenID);
        const tickSize = book.tick_size as "0.1" | "0.01" | "0.001" | "0.0001";
        const negRisk = book.neg_risk;
        const signed = (await c.createOrder({ tokenID, price: Number(tickSize), size: 5, side: Side.BUY }, { tickSize, negRisk })) as any;
        const digest = hashTypedData({
            domain: { name: "Polymarket CTF Exchange", version: "2", chainId: 137, verifyingContract: negRisk ? NEG_RISK_EXCHANGE_V2 : EXCHANGE_V2 },
            types: { Order: ORDER_TYPES },
            primaryType: "Order",
            message: {
                salt: BigInt(signed.salt), maker: signed.maker as Address, signer: signed.signer as Address,
                tokenId: BigInt(signed.tokenId), makerAmount: BigInt(signed.makerAmount), takerAmount: BigInt(signed.takerAmount),
                side: signed.side === Side.BUY ? 0 : 1, signatureType: signed.signatureType,
                timestamp: BigInt(signed.timestamp), metadata: signed.metadata as Hex, builder: signed.builder as Hex,
            },
        });
        const magic = await publicClient.readContract({
            address: signed.maker as Address, abi: erc1271Abi, functionName: "isValidSignature", args: [digest, signed.signature as Hex],
        });
        mkdirSync(OUT_DIR, { recursive: true });
        const dumpFile = `${OUT_DIR}/verify-${Date.now()}.json`;
        writeFileSync(dumpFile, JSON.stringify({ eoa: account.address, negRisk, tickSize, digest, signed, magic }, null, 2));
        console.log(`maker=${signed.maker} signer=${signed.signer} sigType=${signed.signatureType} sigLen=${(signed.signature.length - 2) / 2}`);
        console.log(`orderHash=${digest}`);
        console.log(`isValidSignature -> ${magic} ${magic === "0x1626ba7e" ? "(VALID)" : "(INVALID)"}; dumped to ${dumpFile}`);
    },

    async order(args) {
        const keep = args.includes("--keep");
        const [tokenID, priceArg, sizeArg] = args.filter((a) => a !== "--keep");
        if (!tokenID) throw new Error("usage: order <tokenId> [price] [size] [--keep]");
        const c = await clob(true);
        const book = await c.getOrderBook(tokenID);
        const tickSize = book.tick_size as "0.1" | "0.01" | "0.001" | "0.0001";
        const negRisk = book.neg_risk;
        const price = priceArg ? Number(priceArg) : Number(tickSize);
        const size = sizeArg ? Number(sizeArg) : Math.max(5, Number(book.min_order_size ?? 5));
        console.log(`tick=${tickSize} negRisk=${negRisk} minSize=${book.min_order_size} bestBid=${book.bids?.at(-1)?.price} bestAsk=${book.asks?.at(-1)?.price}`);
        console.log(`Placing post-only GTC BUY ${size} @ ${price} (≈ $${(size * price).toFixed(4)})`);

        const signed = await c.createOrder({ tokenID, price, size, side: Side.BUY }, { tickSize, negRisk });
        mkdirSync(OUT_DIR, { recursive: true });
        const dumpFile = `${OUT_DIR}/order-${Date.now()}.json`;
        writeFileSync(dumpFile, JSON.stringify({ eoa: account.address, negRisk, tickSize, signed }, null, 2));
        console.log(`Signed order dumped to ${dumpFile}`);
        console.log(`  maker=${signed.maker} signer=${signed.signer} sigType=${signed.signatureType} sigLen=${(signed.signature.length - 2) / 2} bytes`);

        const resp = await c.postOrder(signed, OrderType.GTC, true);
        console.log("POST /order ->", JSON.stringify(resp));
        writeFileSync(dumpFile, JSON.stringify({ eoa: account.address, negRisk, tickSize, signed, response: resp }, null, 2));
        if (!resp?.orderID) {
            process.exitCode = 1;
            return;
        }
        if (keep) return;
        console.log("Cancelling ->", JSON.stringify(await c.cancelOrder({ orderID: resp.orderID })));
    },
};

const [cmd, ...rest] = process.argv.slice(2);
const fn = commands[cmd ?? ""];
if (!fn) {
    console.error(`usage: npm run probe -- <${Object.keys(commands).join("|")}> [args]`);
    process.exit(2);
}
fn(rest).catch((e) => {
    console.error(e?.response?.data ?? e);
    process.exit(1);
});
