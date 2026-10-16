package com.kreggscode.polliworld.core;

import com.kreggscode.polliworld.core.MemoryStore.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryStoreTest {

    @Test
    void recentReturnsOldestFirstAndBounded() {
        MemoryStore store = new MemoryStore(24);
        store.add("bjorn", "p1", "user", "hi");
        store.add("bjorn", "p1", "assistant", "hello");
        List<Msg> recent = store.recent("bjorn", "p1", 10);
        assertEquals(2, recent.size());
        assertEquals("hi", recent.get(0).content());
        assertEquals("assistant", recent.get(1).role());
    }

    @Test
    void memoryIsPerNpcAndPerPlayer() {
        MemoryStore store = new MemoryStore(24);
        store.add("bjorn", "p1", "user", "a");
        store.add("bjorn", "p2", "user", "b");
        store.add("tor", "p1", "user", "c");
        assertEquals(1, store.recent("bjorn", "p1", 10).size());
        assertEquals("c", store.recent("tor", "p1", 10).get(0).content());
        assertEquals("b", store.recent("bjorn", "p2", 10).get(0).content());
    }

    @Test
    void oldestMessagesFallOffAtTheCap() {
        MemoryStore store = new MemoryStore(2);
        store.add("n", "p", "user", "one");
        store.add("n", "p", "user", "two");
        store.add("n", "p", "user", "three");
        List<Msg> recent = store.recent("n", "p", 10);
        assertEquals(2, recent.size());
        assertEquals("two", recent.get(0).content());
        assertEquals("three", recent.get(1).content());
    }

    @Test
    void recentHonoursRequestedWindow() {
        MemoryStore store = new MemoryStore(10);
        for (int i = 0; i < 5; i++) {
            store.add("n", "p", "user", "m" + i);
        }
        assertEquals(3, store.recent("n", "p", 3).size());
        assertEquals("m2", store.recent("n", "p", 3).get(0).content());
    }

    @Test
    void roundTripsThroughLines() {
        MemoryStore store = new MemoryStore(24);
        store.add("bjorn", "p1", "user", "first");
        store.add("bjorn", "p1", "assistant", "second");
        MemoryStore back = MemoryStore.fromLines(store.toLines(), 24);
        List<Msg> recent = back.recent("bjorn", "p1", 10);
        assertEquals(2, recent.size());
        assertEquals("first", recent.get(0).content());
        assertEquals("second", recent.get(1).content());
        assertEquals(store.recent("bjorn", "p1", 10).get(0).ts(), recent.get(0).ts());
    }

    @Test
    void escapesPipesAndNewlinesInContent() {
        MemoryStore store = new MemoryStore(24);
        store.add("n|pc", "p1", "user", "a|b\nc");
        MemoryStore back = MemoryStore.fromLines(store.toLines(), 24);
        assertEquals("a|b\nc", back.recent("n|pc", "p1", 10).get(0).content());
    }

    @Test
    void clearRemovesOnlyThatConversation() {
        MemoryStore store = new MemoryStore(24);
        store.add("n", "p1", "user", "x");
        store.add("n", "p2", "user", "y");
        store.clear("n", "p1");
        assertEquals(0, store.size("n", "p1"));
        assertEquals(1, store.size("n", "p2"));
    }

    @Test
    void toleratesEmptyOrNullData() {
        assertEquals(0, MemoryStore.fromLines(null, 8).recent("a", "b", 5).size());
        assertEquals(0, MemoryStore.fromLines("", 8).recent("a", "b", 5).size());
    }
}
