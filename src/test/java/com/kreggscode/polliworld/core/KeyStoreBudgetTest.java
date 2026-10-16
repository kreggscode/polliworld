package com.kreggscode.polliworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyStoreBudgetTest {

    private static final String P1 = "11111111-2222-3333-4444-555555555555";
    private static final String P2 = "99999999-8888-7777-6666-555555555555";

    // ---------------------------------------------------------------- KeyStore

    @Test
    void keyStoreRoundTrips() {
        KeyStore store = new KeyStore();
        store.put(P1, "pk_alpha");
        store.put(P2, "pk_beta");
        KeyStore back = KeyStore.fromLines(store.toLines());
        assertEquals("pk_alpha", back.get(P1));
        assertEquals("pk_beta", back.get(P2));
        assertEquals(2, back.size());
    }

    @Test
    void keyStoreIgnoresBlankAndCorruptLines() {
        KeyStore store = KeyStore.fromLines("\nno-separator\n" + P1 + ":\n  \n");
        assertNull(store.get(P1));
        assertNull(store.get(P2));
        assertEquals(0, store.size());
    }

    @Test
    void removingAKeyClearsIt() {
        KeyStore store = new KeyStore();
        store.put(P1, "pk");
        store.remove(P1);
        assertNull(store.get(P1));
    }

    // ------------------------------------------------------------ BudgetLimiter

    @Test
    void chatCooldownBlocksThenAllows() {
        BudgetLimiter b = new BudgetLimiter(2, 20);
        long t0 = 1_000_000L;
        assertNull(b.denyChatReason(P1, t0));
        b.recordChat(P1, t0);
        assertNotNull(b.denyChatReason(P1, t0 + 500));
        assertTrue(b.denyChatReason(P1, t0 + 500).contains("2s"));
        assertNull(b.denyChatReason(P1, t0 + 2_001));
    }

    @Test
    void cooldownIsPerPlayer() {
        BudgetLimiter b = new BudgetLimiter(5, 20);
        long t0 = 1_000_000L;
        b.recordChat(P1, t0);
        assertNotNull(b.denyChatReason(P1, t0 + 10));
        assertNull(b.denyChatReason(P2, t0 + 10));
    }

    @Test
    void dailyImageBudgetBlocksAtTheLimitAndRollsOver() {
        BudgetLimiter b = new BudgetLimiter(0, 2);
        long day1 = 1_000_000_000L;
        assertNull(b.denyImageReason(P1, day1));
        b.recordImage(P1, day1);
        assertNull(b.denyImageReason(P1, day1));
        b.recordImage(P1, day1);
        assertNotNull(b.denyImageReason(P1, day1));
        assertEquals(2, b.imagesUsedToday(P1, day1));
        // 24h later the budget rolls over.
        long day2 = day1 + 24L * 60 * 60 * 1000;
        assertNull(b.denyImageReason(P1, day2));
        assertEquals(0, b.imagesUsedToday(P1, day2));
    }

    @Test
    void zeroConfiguredBudgetMeansPicturesOff() {
        BudgetLimiter b = new BudgetLimiter(0, 0);
        assertTrue(b.denyImageReason(P1, 1L).contains("turned off"));
    }

    @Test
    void budgetSurvivesRestart() {
        BudgetLimiter b = new BudgetLimiter(2, 20);
        long t0 = 5_000_000L;
        b.recordChat(P1, t0);
        b.recordImage(P1, t0);
        BudgetLimiter back = BudgetLimiter.fromLines(b.toLines(), 2, 20);
        assertNotNull(back.denyChatReason(P1, t0 + 10));
        assertEquals(1, back.imagesUsedToday(P1, t0 + 10));
        // A fresh day resets images but not the player entry bookkeeping.
        assertEquals(0, back.imagesUsedToday(P1, t0 + 24L * 60 * 60 * 1000));
    }
}
