package ml.mypals.ift.track;

import org.jetbrains.annotations.Nullable;

public interface TrackedStack {
	@Nullable
	TrackMark itemflowtracker$getMark();

	void itemflowtracker$setMark(@Nullable TrackMark mark);
}
