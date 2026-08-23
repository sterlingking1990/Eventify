package com.example.PTicketing.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a bank-verified account name plausibly belongs to the organiser
 * requesting a payout.
 *
 * <p>Deliberately loose and deliberately one-sided. Real people's bank records
 * differ from signup names in ordinary ways — middle names dropped, initials
 * added, "Ltd" appended to a personal-name business account — so the bar here is
 * merely <b>one shared name token</b>. Anything sharing none is flagged for an
 * admin to review before approving; nothing is auto-rejected.
 *
 * <p>The looseness is a feature, not a bug: this narrows what a thief with a
 * stolen login can do without breaking legitimate organisers. They now need a
 * bank account whose holder shares a name token with their victim — materially
 * harder than using any account at all — and every flagged request lands in the
 * approval queue with the mismatch visible either way.
 */
public final class AccountNameMatcher {

    private AccountNameMatcher() {
    }

    /**
     * @return true when the two names share at least one name token
     */
    public static boolean looksLikeSamePerson(String organiserName, String accountName) {
        Set<String> organiserTokens = tokens(organiserName);
        if (organiserTokens.isEmpty()) {
            return false;
        }

        for (String token : tokens(accountName)) {
            if (organiserTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lowercases and keeps only alphabetic runs, so "Okafor-Bello Jnr." and
     * "okafor bello jnr" produce identical token sets.
     */
    private static Set<String> tokens(String name) {
        if (name == null) {
            return Set.of();
        }
        return new HashSet<>(Arrays.stream(name.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{Alpha}]+", " ")
                        .trim()
                        .split("\\s+"))
                .filter(t -> !t.isEmpty())
                .toList());
    }
}
