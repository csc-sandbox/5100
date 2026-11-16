package edu.calpoly.lambdalab.jjuangon03;

/** Bounded exponential retry policy with an injectable delay for deterministic tests. */
final class GazeClusterRetryPolicy {
    @FunctionalInterface
    interface Operation { void run(); }

    @FunctionalInterface
    interface Delay { void pause(long milliseconds) throws InterruptedException; }

    private final int maxAttempts;
    private final long initialDelayMillis;
    private final Delay delay;

    GazeClusterRetryPolicy(int maxAttempts, long initialDelayMillis, Delay delay) {
        if (maxAttempts < 1 || initialDelayMillis < 0) throw new IllegalArgumentException("Invalid retry policy");
        this.maxAttempts = maxAttempts;
        this.initialDelayMillis = initialDelayMillis;
        this.delay = delay;
    }

    void execute(Operation operation) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                operation.run();
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt == maxAttempts) break;
                try {
                    delay.pause(initialDelayMillis * (1L << (attempt - 1)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Retry interrupted", interrupted);
                }
            }
        }
        throw lastFailure;
    }
}
