package dev.felippevaz.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Allowlist de IPs (IPv4/IPv6), com suporte a CIDR.
 * <p>
 * Lista vazia = todos os IPs são aceitos (comportamento padrão do servidor).
 */
public final class IpAllowList {

    // Só aceita literais de IP: impede que um hostname dispare uma consulta DNS
    // (e que a allowlist dependa de DNS).
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private final List<Rule> rules = new CopyOnWriteArrayList<>();

    /**
     * @param rule IP exato ("172.18.0.1", "::1") ou bloco CIDR ("10.0.0.0/8", "fd00::/8").
     * @throws IllegalArgumentException se a regra for inválida.
     */
    public void add(String rule) {
        rules.add(Rule.parse(rule));
    }

    public void clear() {
        rules.clear();
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public boolean isAllowed(InetAddress address) {

        if (rules.isEmpty())
            return true;

        if (address == null)
            return false;

        byte[] bytes = normalize(address.getAddress());

        for (Rule rule : rules)
            if (rule.matches(bytes))
                return true;

        return false;
    }

    // "::ffff:a.b.c.d" (IPv4 mapeado em IPv6) é tratado como o próprio IPv4,
    // para que uma regra IPv4 funcione num socket dual-stack.
    private static byte[] normalize(byte[] address) {

        if (address.length != 16)
            return address;

        for (int i = 0; i < 10; i++)
            if (address[i] != 0)
                return address;

        if (address[10] != (byte) 0xff || address[11] != (byte) 0xff)
            return address;

        return new byte[]{address[12], address[13], address[14], address[15]};
    }

    private static final class Rule {

        private final byte[] network;
        private final int prefixLength;

        private Rule(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static Rule parse(String rule) {

            if (rule == null || rule.trim().isEmpty())
                throw new IllegalArgumentException("Empty IP rule");

            String value = rule.trim();
            String ip = value;
            Integer prefix = null;

            int slash = value.indexOf('/');
            if (slash >= 0) {
                ip = value.substring(0, slash);
                try {
                    prefix = Integer.parseInt(value.substring(slash + 1));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid CIDR prefix in rule: " + rule);
                }
            }

            if (!IP_LITERAL.matcher(ip).matches())
                throw new IllegalArgumentException("Not an IP literal: " + rule);

            byte[] bytes;
            try {
                bytes = normalize(InetAddress.getByName(ip).getAddress());
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("Invalid IP in rule: " + rule, exception);
            }

            int maxPrefix = bytes.length * 8;
            int prefixLength = prefix != null ? prefix : maxPrefix;

            if (prefixLength < 0 || prefixLength > maxPrefix)
                throw new IllegalArgumentException("CIDR prefix out of range in rule: " + rule);

            return new Rule(bytes, prefixLength);
        }

        boolean matches(byte[] address) {

            if (address.length != network.length)
                return false;

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++)
                if (address[i] != network[i])
                    return false;

            if (remainingBits == 0)
                return true;

            int mask = (0xff << (8 - remainingBits)) & 0xff;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
