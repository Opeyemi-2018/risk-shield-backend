package RiskShield.analysis;

/** One reason a purchase looks unusual, written as a plain fact. */
public record InsightSignal(SignalCode code, int points, String message) {
}