package com.xiling.city.builder;

/**
 * A user-facing generation failure.
 *
 * <p>{@link #reason()} carries a short, stable label used when aggregating
 * skipped cells in a zone build (e.g. "地形过陡").</p>
 */
public class BuildException extends Exception {

    private final String reason;

    public BuildException(String message) {
        this(message, message);
    }

    public BuildException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
