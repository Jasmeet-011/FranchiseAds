package com.franchiseads.claude;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Records each requested delay and returns at once, so retry tests never wait. */
class RecordingSleeper implements Sleeper {

	private final List<Duration> sleeps = new ArrayList<>();

	@Override
	public void sleep(Duration duration) {
		sleeps.add(duration);
	}

	List<Duration> sleeps() {
		return sleeps;
	}

}
