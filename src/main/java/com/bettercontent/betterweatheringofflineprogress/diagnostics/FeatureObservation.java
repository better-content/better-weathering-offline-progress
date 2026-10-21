package com.bettercontent.betterweatheringofflineprogress.diagnostics;

/** Pure projection of values already observed by the owning native decision. */
public final class FeatureObservation {
    private FeatureObservation() {}
    public static String fields(int candidates, int invoked) { return PlaytestLog.fields("candidates", candidates, "state_mismatch", candidates - invoked, "actions_invoked", invoked, "block_mutations", "UNKNOWN"); }
    
}
