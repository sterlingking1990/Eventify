package com.example.PTicketing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests — the matcher is pure string logic.
 */
class AccountNameMatcherTest {

    @Test
    void identicalNamesMatch() {
        assertTrue(AccountNameMatcher.looksLikeSamePerson(
                "Kingsley Emeka Izundu", "KINGSLEY EMEKA IZUNDU"));
    }

    @Test
    void caseAndPunctuationDifferencesMatch() {
        assertTrue(AccountNameMatcher.looksLikeSamePerson(
                "Kingsley Emeka Izundu", "izundu, emeka-kingsley."));
    }

    @Test
    void middleNamesAndInitialsMatch() {
        assertTrue(AccountNameMatcher.looksLikeSamePerson(
                "Kingsley Emeka Izundu", "Kingsley Izundu"));
        assertTrue(AccountNameMatcher.looksLikeSamePerson(
                "Ada Okafor-Bello", "ADA O BELLO"));
    }

    @Test
    void businessAccountBuiltOnPersonalNameMatches() {
        assertTrue(AccountNameMatcher.looksLikeSamePerson(
                "Kingsley Emeka Izundu", "Izundu Ventures Global Ltd"));
    }

    @Test
    void completelyDifferentNamesDoNotMatch() {
        assertFalse(AccountNameMatcher.looksLikeSamePerson(
                "Kingsley Emeka Izundu", "Femi Balogun"));
        assertFalse(AccountNameMatcher.looksLikeSamePerson(
                "Kingsley Emeka Izundu", "Zenith Bank Nominee Account"));
    }

    @Test
    void sharedSingleCommonTokenIsStillAMatch() {
        // One token in common is enough by design: the flag is soft and the admin
        // queue shows both names. A thief must now source an account whose holder
        // shares a name token with their victim — not just any account.
        assertTrue(AccountNameMatcher.looksLikeSamePerson(
                "Kingsley Emeka Izundu", "Emeka Enterprises"));
    }

    @Test
    void emptyOrBlankInputsNeverMatch() {
        assertFalse(AccountNameMatcher.looksLikeSamePerson(null, "Someone"));
        assertFalse(AccountNameMatcher.looksLikeSamePerson("Someone", null));
        assertFalse(AccountNameMatcher.looksLikeSamePerson("", "   "));
        assertFalse(AccountNameMatcher.looksLikeSamePerson("12345", "67890"));
    }
}
