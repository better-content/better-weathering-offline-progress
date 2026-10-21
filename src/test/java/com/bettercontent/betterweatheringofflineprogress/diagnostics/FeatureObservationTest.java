package com.bettercontent.betterweatheringofflineprogress.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class FeatureObservationTest {
    @Test public void nativeScalarsHaveExplicitStage() { var fields = FeatureObservation.fields(3, 2); assertTrue(fields.contains("candidates=3")); assertTrue(fields.contains("state_mismatch=1")); assertTrue(fields.contains("actions_invoked=2")); }
    @Test public void uncertaintyAndPrivacyStayExplicit() { var fields = FeatureObservation.fields(1, 1); assertTrue(fields.contains("block_mutations=UNKNOWN")); assertFalse(fields.contains("block_mutations=1")); assertFalse(fields.contains("durable")); }
}
