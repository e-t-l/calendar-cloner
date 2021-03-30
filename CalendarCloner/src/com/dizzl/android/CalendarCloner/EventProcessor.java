package com.dizzl.android.CalendarCloner;

import org.joda.time.DateTime;

public class EventProcessor extends Processor {
	private static final long ONE_DAY = 24L * 3600 * 1000;
	private static final long INFINITY = 1000 * 365 * ONE_DAY;

	protected AttendeesTable mAttendeesTable = null;
	protected RemindersTable mRemindersTable = null;

	// The source calendar
	protected DbCalendar mSrcCalendar = null;

	// The destination calendar
	protected DbCalendar mDstCalendar = null;

	private Period mSyncPeriod = null;

	protected Rule mRule;

	protected int mEventNr = 0;

	public class EventResult {
		protected boolean completed = true;
		protected int updateCount = 0;
	}

	public EventProcessor(Rule rule) {
		mRule = rule;
		ClonerDb db = ClonerApp.getDb(rule.isReadOnly());
		mAttendeesTable = new AttendeesTable(db);
		mRemindersTable = new RemindersTable(db);
	}

	public long getDstCalendarId() {
		if (mDstCalendar != null) {
			return mDstCalendar.getId();
		}
		return -1;
	}

	public long getSrcCalendarId() {
		if (mSrcCalendar != null) {
			return mSrcCalendar.getId();
		}
		return -1;
	}

	private boolean isAvailabilitySelected(Availabilities avs, int availability) {
		// Map Availability to Availabilities values
		switch (availability) {
		case Availabilities.AVAILABILITY_FREE:
		case Availabilities.AVAILABILITY_TENTATIVE:
		case Availabilities.AVAILABILITY_BUSY:
			return avs.isKeySelected(availability);
		}
		return false;
	}

	private boolean isAvailabilitySamsungSelected(Availabilities avs, int availability) {
		// Map Samsung Availability to Availabilities values
		switch (availability) {
		case Device.Samsung.AVAILABILITY_FREE:
			return avs.isKeySelected(Availabilities.AVAILABILITY_FREE);
		case Device.Samsung.AVAILABILITY_TENTATIVE:
			return avs.isKeySelected(Availabilities.AVAILABILITY_TENTATIVE);
		case Device.Samsung.AVAILABILITY_BUSY:
			return avs.isKeySelected(Availabilities.AVAILABILITY_BUSY);
		case Device.Samsung.AVAILABILITY_OUT_OF_OFFICE:
			return avs.isKeySelected(Availabilities.AVAILABILITY_OUT_OF_OFFICE);
		}
		return false;
	}

	private String isEventSelectedByRule(Event event) {
		// Event Type filter
		if ((mRule.getEventTypeFilter() == Rule.EVENT_TYPE_SIMPLE && event.isRecurringEvent())
				|| (mRule.getEventTypeFilter() == Rule.EVENT_TYPE_RECURRING && !event.isRecurringEvent())) {
			return ClonerApp.translate(R.string.rule_event_type_filter);
		}

		// Title Must Contain filter
		if (!mRule.getTitleMustContain().contentEquals("")) {
			if (event.getTitle() == null || !event.getTitle().contains(mRule.getTitleMustContain())) {
				return ClonerApp.translate(R.string.rule_title_must_contain);
			}
		}

		// Title Must Not Contain filter
		if (!mRule.getTitleMustNotContain().contentEquals("")) {
			if (event.getTitle() != null && event.getTitle().contains(mRule.getTitleMustNotContain())) {
				return ClonerApp.translate(R.string.rule_title_must_not_contain);
			}
		}

		// Location Must Contain filter
		if (!mRule.getLocationMustContain().contentEquals("")) {
			if (event.getLocation() == null || !event.getLocation().contains(mRule.getLocationMustContain())) {
				return ClonerApp.translate(R.string.rule_location_must_contain);
			}
		}

		// Location Must Not Contain filter
		if (!mRule.getLocationMustNotContain().contentEquals("")) {
			if (event.getLocation() != null && event.getLocation().contains(mRule.getLocationMustNotContain())) {
				return ClonerApp.translate(R.string.rule_location_must_not_contain);
			}
		}

		// Description Must Contain filter
		if (!mRule.getDescriptionMustContain().contentEquals("")) {
			if (event.getDescription() == null || !event.getDescription().contains(mRule.getDescriptionMustContain())) {
				return ClonerApp.translate(R.string.rule_description_must_contain);
			}
		}

		// Description Must Not Contain filter
		if (!mRule.getDescriptionMustNotContain().contentEquals("")) {
			if (event.getDescription() != null || event.getDescription().contains(mRule.getDescriptionMustNotContain())) {
				return ClonerApp.translate(R.string.rule_description_must_not_contain);
			}
		}

		// Access Level filter
		int accessLevel = event.getAccessLevel();
		if (!mRule.getAccessLevels().isKeySelected(accessLevel)) {
			return ClonerApp.translate(R.string.rule_access_level_filter);
		}

		// Attendance filter
		int attendeeStatus = event.getSelfAttendeeStatus();
		if (!mRule.getAttendeeStatuses().isKeySelected(attendeeStatus)) {
			return ClonerApp.translate(R.string.rule_attendance_filter);
		}

		// Availability filter
		if (event.hasAvailabilitySamsung()) {
			if (!this.isAvailabilitySamsungSelected(mRule.getAvailabilities(), event.getAvailabilitySamsung())) {
				return ClonerApp.translate(R.string.rule_availability_filter);
			}
		} else {
			if (!this.isAvailabilitySelected(mRule.getAvailabilities(), event.getAvailability())) {
				return ClonerApp.translate(R.string.rule_availability_filter);
			}
		}

		// Event Status filter
		int eventStatus = event.getStatus();
		if (!mRule.getEventStatuses().isKeySelected(eventStatus)) {
			return ClonerApp.translate(R.string.rule_event_status_filter);
		}

		if (!event.isRecurringEvent()) {
			// Find out if the event falls on one of the weekdays to process
			// events for
			DateTime start = event.getStartTime();
			DateTime end = event.getEndTime();

			boolean cloneableWeekday = false;
			while (!cloneableWeekday && !start.isAfter(end)) {
				int weekday = start.getDayOfWeek();
				cloneableWeekday = mRule.getWeekdays().isKeySelected(weekday);
				start = start.plusDays(1);
			}

			if (!cloneableWeekday) {
				return ClonerApp.translate(R.string.rule_weekday_filter);
			}
		}

		return null;
	}

	protected String isEventSelected(Event event) {
		// Check if the type of event is selected
		if (!mRule.getIncludeClones()) {
			if (EventMarker.getEventType(event) != EventMarker.TYPE_NORMAL) {
				return ClonerApp.translate(R.string.rule_include_clones);
			}
		}

		// Check if the event lies within the sync period
		Period eventPeriod = event.getPeriod();

		if (!eventPeriod.overlaps(mSyncPeriod)) {
			if (eventPeriod.endsBeforeStartOf(mSyncPeriod)) {
				return ClonerApp.translate(R.string.rule_sync_period_before);
			}
			if (eventPeriod.startsAfterEndOf(mSyncPeriod)) {
				return ClonerApp.translate(R.string.rule_sync_period_after);
			}
		}

		// Filter out events according to the rule's filters
		if (mRule.useEventFilters()) {
			String selectedMsg = this.isEventSelectedByRule(event);
			if (selectedMsg != null) {
				return selectedMsg;
			}
		}

		return null;
	}

	protected void skipSingleEvent(Event event) {
		this.logSummary(ClonerLog.LOG_INFO, ClonerApp.translate(R.string.cloner_log_insync), event);
	}

	protected void skipRecurringEvent(Event event) {
		this.logSummary(ClonerLog.LOG_INFO, ClonerApp.translate(R.string.cloner_log_insync), event);
	}

	protected EventResult createEventResult() {
		return new EventResult();
	}

	protected EventResult processSingleEvent(Event event) {
		return this.createEventResult();
	}

	protected EventResult processRecurringEvent(Event event) {
		return this.createEventResult();
	}

	public EventResult process(Event event, ClonerLog log, String logPrefix) {
		// Initialize the logging context
		this.initLogLines(log, logPrefix);
		// Keep track of whether the clone of the event is updated
		EventResult result = null;
		// Check if we need to clone this event
		String selectedMsg = this.isEventSelected(event);
		if (selectedMsg == null) {
			if (!event.isRecurringEvent()) {
				// Copy all the relevant fields from the source event to the
				// destination event
				result = this.processSingleEvent(event);
			} else {
				// Copy all the relevant fields from the source event to the
				// destination event
				result = this.processRecurringEvent(event);
			}

			// Event is now processed
		} else {
			this.logSummary(ClonerLog.LOG_INFO, ClonerApp.translate(R.string.cloner_log_skipped), event,
					ClonerApp.translate(R.string.cloner_log_filter_x, new String[] { selectedMsg }));
		}

		return result != null ? result : new EventResult();
	}

	@Override
	public InitResult init(ClonerLog log) {
		InitResult result = super.init(log);
		if (!result.isSuccess()) {
			return result;
		}

		// Find the source calendar
		CalendarLoader.CalendarInfo info = CalendarLoader.getCalendarByRef(mRule.getSrcCalendarRef());
		if (info.getCalendar() == null) {
			return new InitResult(false, CalendarLoader.getErrorString(info.getError(),
					ClonerApp.translate(R.string.calendar_source)), result.getUpdateCount());
		}
		mSrcCalendar = info.getCalendar();
		if (!mSrcCalendar.isLocal() && !mSrcCalendar.isSynchronized()) {
			return new InitResult(false, ClonerApp.translate(R.string.error_calendar_x_not_synchronized,
					new String[] { ClonerApp.translate(R.string.calendar_source) }), result.getUpdateCount());
		}

		// Find the destination calendar
		info = CalendarLoader.getCalendarByRef(mRule.getDstCalendarRef());
		if (info.getCalendar() == null) {
			return new InitResult(false, CalendarLoader.getErrorString(info.getError(),
					ClonerApp.translate(R.string.calendar_destination)), result.getUpdateCount());
		}
		mDstCalendar = info.getCalendar();
		if (!mDstCalendar.isLocal() && !mDstCalendar.isSynchronized()) {
			return new InitResult(false, ClonerApp.translate(R.string.error_calendar_x_not_synchronized,
					new String[] { ClonerApp.translate(R.string.calendar_destination) }), result.getUpdateCount());
		}
		if (!mDstCalendar.isWriteable()) {
			return new InitResult(false, ClonerApp.translate(R.string.error_calendar_x_not_writeable,
					new String[] { ClonerApp.translate(R.string.calendar_destination) }), result.getUpdateCount());
		}

		// Save current time for event period selection
		DateTime periodStart = new DateTime().minus(
//				mRule.getSyncPeriodBefore() != 0 ? mRule.getSyncPeriodBefore() : INFINITY).withTime(0, 0, 0, 0);
				mRule.getSyncPeriodBefore()).withTime(0, 0, 0, 0);
		DateTime periodEnd = new DateTime()
//				.plus(mRule.getSyncPeriodAfter() != 0 ? mRule.getSyncPeriodAfter() : INFINITY).plusDays(1)
				.plus(mRule.getSyncPeriodAfter()).plusDays(1)
				.withTime(0, 0, 0, 0);
		mSyncPeriod = new Period(periodStart, periodEnd);

		return result;
	}

	public EventResult roundup(ClonerLog log) {
		return this.createEventResult();
	}
}
