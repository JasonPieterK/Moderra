package com.jason.plugins.moderra.engine.filters;

import com.jason.plugins.moderra.engine.EffectiveSettings;
import com.jason.plugins.moderra.engine.Hit;
import com.jason.plugins.moderra.engine.Message;
import com.jason.plugins.moderra.engine.TextCheck;
import com.jason.plugins.moderra.mod.Names;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The link filter: finds links with the {@link LinkParser} and applies the admin's policy (mode, allow and block lists,
 * IP switch, invite switch). Stateless and thread-safe.
 */
public final class LinkCheck implements TextCheck {
    public static final String ID = "link";

    /** One list entry, ready to compare. */
    private record Rule(String host, String path, Pattern glob, boolean suffix, boolean subdomainsOnly) {
        boolean matches(String linkHost, String linkPath) {
            if (glob != null) {
                return glob.matcher(linkHost).matches() || glob.matcher(linkHost + linkPath).matches();
            }
            boolean hostOk = suffix ? linkHost.endsWith(host)
                : subdomainsOnly ? linkHost.endsWith("." + host)
                : linkHost.equals(host) || linkHost.endsWith("." + host);
            return hostOk && (path.isEmpty() || linkPath.startsWith(path));
        }
    }

    private record Policy(String mode, List<Rule> allowed, List<Rule> blocked, boolean blockIps, boolean blockInvites, List<Rule> invites,
                          List<Rule> allowedInvites, LinkParser.Options parser) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean enabled(EffectiveSettings settings) {
        return !LinkSettings.MODE_OFF.equals(settings.get(LinkSettings.MODE));
    }

    @Override
    public String feedback(Hit hit, EffectiveSettings settings) {
        return Names.colour(settings.get(LinkSettings.MESSAGE));
    }

    @Override
    public Optional<Hit> check(Message message, EffectiveSettings s) {
        if (!FilterAction.covers(s.get(LinkSettings.SOURCES), message) || message.has(LinkSettings.PERMISSION_BYPASS)) {
            return Optional.empty();
        }
        Policy policy = s.derived("link.policy", LinkCheck::compile);
        String text = message.text();
        List<LinkParser.Link> caught = new ArrayList<>();
        String detail = null;
        for (LinkParser.Link link : LinkParser.find(text, policy.parser())) {
            String reason = judge(link, policy);
            if (reason != null) {
                caught.add(link);
                if (detail == null) {
                    detail = reason;
                }
            }
        }
        if (caught.isEmpty()) {
            return Optional.empty();
        }
        String replacement = s.get(LinkSettings.REPLACEMENT);
        StringBuilder cleaned = new StringBuilder(text.length());
        int at = 0;
        for (LinkParser.Link link : caught) {
            if (link.start() < at) {
                continue; // overlaps the previous one
            }
            cleaned.append(text, at, link.start()).append(replacement);
            at = link.end();
        }
        cleaned.append(text, at, text.length());
        return Optional.of(FilterAction.hit(ID, detail, s.get(LinkSettings.ACTION), s.get(LinkSettings.WEIGHT), cleaned.toString()));
    }

    /** @return why the link is not allowed (shown in logs and {@code /moderra check}), or {@code null} when it is fine */
    private static String judge(LinkParser.Link link, Policy policy) {
        String host = link.host();
        String path = link.path();
        if (link.kind() != LinkParser.Kind.DOMAIN) {
            if (!policy.blockIps() || any(policy.allowed(), host, path)) {
                return null;
            }
            return "IP address " + host;
        }
        if (policy.blockInvites() && any(policy.invites(), host, path)) {
            return any(policy.allowedInvites(), host, path) ? null : "invite link " + host + path;
        }
        return switch (policy.mode()) {
            case LinkSettings.MODE_BLOCK_ALL -> "link " + host;
            case LinkSettings.MODE_ALLOW_LIST -> any(policy.allowed(), host, path) ? null : "link " + host;
            case LinkSettings.MODE_BLOCK_LIST -> any(policy.blocked(), host, path) ? "link " + host : null;
            default -> null;
        };
    }

    private static boolean any(List<Rule> rules, String host, String path) {
        for (Rule rule : rules) {
            if (rule.matches(host, path)) {
                return true;
            }
        }
        return false;
    }

    private static Policy compile(EffectiveSettings s) {
        Set<String> endings = new HashSet<>();
        for (String ending : s.get(LinkSettings.ENDINGS)) {
            String e = ending.trim().toLowerCase(Locale.ROOT);
            while (e.startsWith(".")) {
                e = e.substring(1);
            }
            if (!e.isEmpty()) {
                endings.add(e);
            }
        }
        List<String> dotWords = s.get(LinkSettings.DOT_WORDS).stream().map(w -> w.trim().toLowerCase(Locale.ROOT)).filter(w -> !w.isEmpty()).toList();
        List<Rule> allowed = rules(s.get(LinkSettings.ALLOWED));
        List<Rule> blocked = rules(s.get(LinkSettings.BLOCKED));
        List<Rule> invites = rules(s.get(LinkSettings.INVITE_LINKS));
        // domains the admin named are links even when their ending is not in domain-endings ("t.me")
        Set<String> known = new HashSet<>();
        for (List<Rule> list : List.of(allowed, blocked, invites)) {
            for (Rule rule : list) {
                if (rule.glob() == null && !rule.suffix() && rule.host().indexOf('.') > 0) {
                    known.add(rule.host());
                }
            }
        }
        return new Policy(s.get(LinkSettings.MODE), allowed, blocked, s.get(LinkSettings.BLOCK_IPS), s.get(LinkSettings.BLOCK_INVITES),
            invites, rules(s.get(LinkSettings.ALLOWED_INVITES)),
            new LinkParser.Options(endings, dotWords, s.get(LinkSettings.DETECT_OBFUSCATED), known));
    }

    private static List<Rule> rules(List<String> entries) {
        List<Rule> out = new ArrayList<>();
        for (String entry : entries) {
            String e = entry.trim().toLowerCase(Locale.ROOT);
            int scheme = e.indexOf("://");
            if (scheme >= 0) {
                e = e.substring(scheme + 3);
            }
            if (e.startsWith("www.")) {
                e = e.substring(4);
            }
            while (e.endsWith("/")) {
                e = e.substring(0, e.length() - 1);
            }
            if (e.isEmpty()) {
                continue;
            }
            int slash = e.indexOf('/');
            String host = slash < 0 ? e : e.substring(0, slash);
            String path = slash < 0 ? "" : e.substring(slash);
            if (host.startsWith("*.") && host.indexOf('*', 1) < 0) {
                out.add(new Rule(host.substring(2), path, null, false, true));
            } else if (e.indexOf('*') >= 0 || e.indexOf('?') >= 0) {
                StringBuilder regex = new StringBuilder();
                for (char c : e.toCharArray()) {
                    regex.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
                }
                out.add(new Rule(host, path, Pattern.compile(regex.toString()), false, false));
            } else if (host.startsWith(".")) {
                out.add(new Rule(host, path, null, true, false));
            } else {
                out.add(new Rule(host, path, null, false, false));
            }
        }
        return out;
    }
}
