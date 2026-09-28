package com.s2admin.module.common.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCacheStoreTest {

    private final InMemoryCacheStore store = new InMemoryCacheStore();

    @Test
    void setAndGet() {
        store.set("k", "v", Duration.ofMinutes(5));
        assertEquals("v", store.get("k"));
        assertTrue(store.hasKey("k"));
        store.delete("k");
        assertNull(store.get("k"));
        assertFalse(store.hasKey("k"));
    }

    @Test
    void entryExpiresAfterTtl() throws InterruptedException {
        store.set("k", "v", Duration.ofMillis(50));
        Thread.sleep(120);
        assertNull(store.get("k"));
        assertFalse(store.hasKey("k"));
    }

    @Test
    void getAndDeleteRemovesEntry() {
        store.set("k", "v", Duration.ofMinutes(5));
        assertEquals("v", store.getAndDelete("k"));
        assertNull(store.get("k"));
    }

    @Test
    void setIfAbsentIsOnceOnly() {
        assertTrue(store.setIfAbsent("once", "1", Duration.ofMinutes(5)));
        assertFalse(store.setIfAbsent("once", "2", Duration.ofMinutes(5)));
        assertEquals("1", store.get("once"));
    }

    @Test
    void getAndDeleteOfExpiredEntryReturnsNull() throws InterruptedException {
        store.set("k", "v", Duration.ofMillis(50));
        Thread.sleep(120);
        assertNull(store.getAndDelete("k"));
    }

    @Test
    void incrementStartsAtOneAndAccumulates() {
        assertEquals(1, store.increment("cnt"));
        assertEquals(2, store.increment("cnt"));
        assertEquals(3, store.increment("cnt"));
        assertEquals("3", store.get("cnt"));
    }

    @Test
    void expireResetsTtl() throws InterruptedException {
        store.set("k", "v", Duration.ofMillis(30));
        store.expire("k", Duration.ofMinutes(10));
        Thread.sleep(80);
        assertEquals("v", store.get("k"));
    }

    @Test
    void incrementResetsAfterExpiry() throws InterruptedException {
        store.set("cnt", "5", Duration.ofMillis(30));
        Thread.sleep(80);
        assertEquals(1, store.increment("cnt"));
    }
}
