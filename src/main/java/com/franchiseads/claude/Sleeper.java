package com.franchiseads.claude;

import java.time.Duration;

/** Waits between retry attempts. Production uses {@code Thread::sleep}; tests record the delays instead (ADR-009). */
@FunctionalInterface
public interface Sleeper {

	void sleep(Duration duration) throws InterruptedException;

}
