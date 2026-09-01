package org.levimc.launcher.core.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MinecraftRuntimePreparerTest {
    @Test
    public void enablesCompatibilityModeForAndroid15AndMinecraft12645() {
        assertTrue(MinecraftRuntimePreparer.shouldUseGxCoreCompatibilitySafeMode("1.26.45", 35));
        assertTrue(MinecraftRuntimePreparer.shouldUseGxCoreCompatibilitySafeMode("1.26.45.1", 35));
        assertTrue(MinecraftRuntimePreparer.shouldUseGxCoreCompatibilitySafeMode("1.26.45.1", 36));
    }

    @Test
    public void keepsGxCoreForOtherVersionsAndOlderAndroid() {
        assertFalse(MinecraftRuntimePreparer.shouldUseGxCoreCompatibilitySafeMode("1.26.45.1", 34));
        assertFalse(MinecraftRuntimePreparer.shouldUseGxCoreCompatibilitySafeMode("1.26.44", 35));
        assertFalse(MinecraftRuntimePreparer.shouldUseGxCoreCompatibilitySafeMode("1.26.50", 35));
        assertFalse(MinecraftRuntimePreparer.shouldUseGxCoreCompatibilitySafeMode(null, 35));
    }
}
