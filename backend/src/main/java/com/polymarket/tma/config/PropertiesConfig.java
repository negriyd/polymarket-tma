package com.polymarket.tma.config;

import com.polymarket.tma.trading.bridge.BridgeProperties;
import com.polymarket.tma.trading.relayer.RelayerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, RelayerProperties.class, BridgeProperties.class})
public class PropertiesConfig {
}
