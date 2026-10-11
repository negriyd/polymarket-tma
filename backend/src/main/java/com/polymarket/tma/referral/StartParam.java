package com.polymarket.tma.referral;

import java.util.regex.Pattern;

/**
 * Telegram {@code ?startapp=} value of our links: {@code _}-separated tokens in any order —
 * {@code m_<marketId>} market, {@code y}/{@code n} sharer's pick, {@code r<code>} referrer,
 * {@code s<tag>} channel. E.g. {@code m_559652_y_rk3f9a2qx}, {@code stg1}, {@code r8h2kq0za_stg1}.
 * Telegram allows {@code [A-Za-z0-9_-]{1,64}}.
 */
public record StartParam(String marketId, String side, String refCode, String source) {

    private static final Pattern REF = Pattern.compile("r([a-z0-9]{4,16})");
    private static final Pattern SOURCE = Pattern.compile("s([a-z0-9-]{1,30})");
    private static final Pattern DIGITS = Pattern.compile("\\d{1,20}");

    public static final StartParam NONE = new StartParam(null, null, null, null);

    public static StartParam parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 64) {
            return NONE;
        }
        String market = null;
        String side = null;
        String ref = null;
        String source = null;
        String[] tokens = raw.split("_");
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            if (tok.equals("m") && i + 1 < tokens.length && DIGITS.matcher(tokens[i + 1]).matches()) {
                market = tokens[++i];
            } else if (tok.equals("y")) {
                side = "yes";
            } else if (tok.equals("n")) {
                side = "no";
            } else if (REF.matcher(tok).matches()) {
                ref = tok.substring(1);
            } else if (SOURCE.matcher(tok).matches()) {
                source = tok.substring(1);
            }
        }
        return new StartParam(market, side, ref, source);
    }
}
