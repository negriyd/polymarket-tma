package com.polymarket.tma.wallet;

import com.polymarket.tma.config.AppProperties;
import java.math.BigInteger;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

/**
 * Optional on-chain reader for current approval state. Uses the Polygon JSON-RPC endpoint
 * configured in {@code app.polygon.rpc-url}. If RPC fails (CI / offline) the reader returns
 * {@code null} so callers can degrade gracefully and still surface approval calldata to the UI.
 */
@Component
public class ApprovalStatusReader {

    private static final Logger log = LoggerFactory.getLogger(ApprovalStatusReader.class);

    private final AppProperties props;
    private volatile Web3j web3j;

    public ApprovalStatusReader(AppProperties props) {
        this.props = props;
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

    /** {@code allowance(owner, spender)} on the USDC contract; {@code null} on RPC failure. */
    public BigInteger usdcAllowance(String owner, String spender) {
        Function fn = new Function(
                "allowance",
                List.of(new Address(owner), new Address(spender)),
                List.of(new TypeReference<Uint256>() {}));
        return callForUint256(props.polygon().usdcAddress(), owner, fn);
    }

    /** {@code isApprovedForAll(owner, operator)} on the CTF contract; {@code null} on RPC failure. */
    public Boolean ctfIsApprovedForAll(String owner, String operator) {
        Function fn = new Function(
                "isApprovedForAll",
                List.of(new Address(owner), new Address(operator)),
                List.of(new TypeReference<Bool>() {}));
        String address = props.polygon().ctfAddress();
        if (address == null || address.isBlank()) {
            return null;
        }
        try {
            String data = FunctionEncoder.encode(fn);
            EthCall resp = web3j().ethCall(
                    Transaction.createEthCallTransaction(owner, address, data),
                    DefaultBlockParameterName.LATEST).send();
            if (resp.hasError() || resp.getValue() == null) {
                return null;
            }
            List<Type> out = FunctionReturnDecoder.decode(resp.getValue(), fn.getOutputParameters());
            if (out.isEmpty()) {
                return null;
            }
            return ((Bool) out.get(0)).getValue();
        } catch (Exception e) {
            log.warn("RPC isApprovedForAll failed: {}", e.toString());
            return null;
        }
    }

    private BigInteger callForUint256(String contract, String from, Function fn) {
        if (contract == null || contract.isBlank()) {
            return null;
        }
        try {
            String data = FunctionEncoder.encode(fn);
            EthCall resp = web3j().ethCall(
                    Transaction.createEthCallTransaction(from, contract, data),
                    DefaultBlockParameterName.LATEST).send();
            if (resp.hasError() || resp.getValue() == null) {
                return null;
            }
            List<Type> out = FunctionReturnDecoder.decode(resp.getValue(), fn.getOutputParameters());
            if (out.isEmpty()) {
                return null;
            }
            return ((Uint256) out.get(0)).getValue();
        } catch (Exception e) {
            log.warn("RPC eth_call to {} failed: {}", contract, e.toString());
            return null;
        }
    }
}
