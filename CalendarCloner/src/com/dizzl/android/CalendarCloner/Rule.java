package com.dizzl.android.CalendarCloner;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.provider.CalendarContract.Reminders;

public class Rule {
	public final static int CURRENT_RULE_VERSION = 2;

	public final static int METHOD_CLONE = 0;
	public final static int METHOD_LEGACY_FORWARD = 1;
	public final static int METHOD_MOVE = 2;
	public final static int METHOD_AGGREGATE = 3;

	public final static int EVENT_TYPE_ALL = 0;
	public final static int EVENT_TYPE_SIMPLE = 1;
	public final static int EVENT_TYPE_RECURRING = 2;

	public final static int DETAIL_ALLDETAILS = 0;
	public final static int DETAIL_TITLELOCATION = 1;
	public final static int DETAIL_FREEBUSY = 2;

	public final static int CUSTOM_ACCESS_LEVEL_SOURCE = 0;
	public final static int CUSTOM_ACCESS_LEVEL_DEFAULT = 1;
	public final static int CUSTOM_ACCESS_LEVEL_PRIVATE = 2;
	public final static int CUSTOM_ACCESS_LEVEL_CONFIDENTIAL = 3;
	public final static int CUSTOM_ACCESS_LEVEL_PUBLIC = 4;

	public final static int CUSTOM_AVAILABILITY_SOURCE = 0;
	public final static int CUSTOM_AVAILABILITY_FREE = 1;
	public final static int CUSTOM_AVAILABILITY_BUSY = 2;
	public final static int CUSTOM_AVAILABILITY_OUT_OF_OFFICE = 3;

	public final static int HASH_METHOD_SOURCE_CALENDAR = 0;
	public final static int HASH_METHOD_RULE_ID = 1;
	public final static int HASH_METHOD_MANUAL = 2;

	private final static long ONE_DAY = 24L * 3600 * 1000;
	private final static long MAX_AGGREGATE_RULE_PERIOD = 365 * ONE_DAY;
	public final static long SYNC_PERIODS[] = { 0, ONE_DAY, 3 * ONE_DAY, 7 * ONE_DAY, 14 * ONE_DAY, 30 * ONE_DAY,
			90 * ONE_DAY, MAX_AGGREGATE_RULE_PERIOD, 2 * 365 * ONE_DAY, 5 * 365 * ONE_DAY, Utilities.INFINITY_MILLIS, };

	// Dummy domains
	public final static String DEFAULT_DUMMY_DOMAIN = "cc.dizzl.com";
	public final static String BLACKHOLE_DOMAIN = "blackhole.io";

	// Rule identifier
	private String mId = "";
	private String mHash = "";

	// Basic rule settings
	private boolean mEnabled = false;
	private String mName = "";
	private int mMethod = ClonerVersion.setRuleMethod(METHOD_CLONE);
	private String mSrcCalendarRef = "";
	private String mDstCalendarRef = "";
	private String mSrcCalendarHash = "";
	private boolean mReadOnly = false;

	// Rule event selection
	private long mSyncPeriodBefore = Utilities.INFINITY_MILLIS;
	private long mSyncPeriodAfter = Utilities.INFINITY_MILLIS;
	private boolean mIncludeClones = ClonerVersion.setIncludeClones(false);
	private boolean mUseEventFilters = ClonerVersion.setUseEventFilters(false);
	private int mEventTypeFilter = EVENT_TYPE_ALL;
	private String mTitleMustContain = "";
	private String mTitleMustNotContain = "";
	private String mLocationMustContain = "";
	private String mLocationMustNotContain = "";
	private String mDescriptionMustContain = "";
	private String mDescriptionMustNotContain = "";
	private AccessLevels mAccessLevels = new AccessLevels(true);
	private AttendeeStatuses mAttendeeStatuses = new AttendeeStatuses(true);
	private Availabilities mAvailabilities = new Availabilities(true);
	private EventStatuses mEventStatuses = new EventStatuses(true);
	private Weekdays mWeekdays = new Weekdays(true);

	// Detail options
	private boolean mCloneTitle = true;
	private String mCustomTitle = "";
	private boolean mCloneLocation = true;
	private String mCustomLocation = "";
	private boolean mCloneDescription = true;
	private String mCustomDescription = "";
	private boolean mCloneSelfAttendeeStatus = false;
	private boolean mCloneSelfAttendeeStatusReverse = false;
	private String mSelfAttendeeName = "";
	private int mReserveBefore = 0;
	private int mReserveAfter = 0;
	private int mCustomAccessLevel = CUSTOM_ACCESS_LEVEL_SOURCE;
	private int mCustomAvailability = CUSTOM_AVAILABILITY_SOURCE;

	// Guest list options
	private boolean mCloneAttendees = ClonerVersion.setCloneAttendees(false);
	private boolean mUseDummyEmailAddresses = true;
	private String mDummyEmailDomain = DEFAULT_DUMMY_DOMAIN;
	private boolean mAttendeesAsText = false;
	private boolean mCustomAttendee = ClonerVersion.setCustomAttendee(false);
	private String mCustomAttendeeName = "";
	private String mCustomAttendeeEmail = "";

	// Reminder options
	private boolean mCloneReminders = ClonerVersion.setCloneReminders(false);
	private boolean mCustomReminder = ClonerVersion.setCustomReminder(false);
	private int mCustomReminderMethod = Reminders.METHOD_DEFAULT;
	private int mCustomReminderMinutes = Reminders.MINUTES_DEFAULT;

	// Additional rule options
	private boolean mRetainClonesOutsideSourceEventWindow = false;

	// Advanced parameters
	private int mHashMethod = HASH_METHOD_SOURCE_CALENDAR;

	private final static int LOCK_SECRET = 1955824592;
	private int mLocked = 0;
	private boolean mExecuting = false;
	private boolean mExecuted = false;
	private int mStatus = RuleExecutor.Result.STATUS_SUCCESS;
	private boolean mIsDirty = false;
	private ClonerLog mLog = null;
	private String mSummary = "";

	public Rule() {
		this.setId("");
	}

	public Rule(CalendarsTable table, String id, boolean enabled, String name, int method, String srcCalendarRef,
			String dstCalendarRef, String srcCalendarHash, boolean readOnly, long syncPeriodBefore,
			long syncPeriodAfter, boolean includeClones, boolean useEventFilters, int eventTypeFilter,
			String titleMustContain, String titleMustNotContain, String locationMustContain,
			String locationMustNotContain, String descriptionMustContain, String descriptionMustNotContain,
			AccessLevels accessLevels, AttendeeStatuses attendeeStatuses, Availabilities availabilities,
			EventStatuses eventStatuses, Weekdays weekdays, boolean cloneTitle, String customTitle,
			boolean cloneLocation, String customLocation, boolean cloneDescription, String customDescription,
			boolean cloneSelfAttendeeStatus, boolean cloneSelfAttendeeStatusReverse, String selfAttendeeName,
			int reserveBefore, int reserveAfter, int accessLevelOverride, int availabilityOverride,
			boolean cloneAttendees, boolean useDummyEmailAddresses, String dummyEmailDomain, boolean attendeesAsText,
			boolean customAttendee, String customAttendeeName, String customAttendeeEmail, boolean cloneReminders,
			boolean customReminder, int customReminderMethod, int customReminderMinutes,
			boolean retainClonesOutsideSourceEventWindow, int hashMethod, String hash) {
		// Basic rule settings
		this.setId(id);
		this.setEnabled(enabled);
		this.setName(name);
		this.setMethod(method);
		this.setSrcCalendarRef(srcCalendarRef);
		this.setDstCalendarRef(dstCalendarRef);
		mSrcCalendarHash = srcCalendarHash;
		this.setReadOnly(readOnly);

		// Event filters
		this.setSyncPeriodBefore(syncPeriodBefore);
		this.setSyncPeriodAfter(syncPeriodAfter);
		this.setIncludeClones(includeClones);
		this.setUseEventFilters(useEventFilters);
		this.setEventTypeFilter(eventTypeFilter);
		this.setTitleMustContain(titleMustContain);
		this.setTitleMustNotContain(titleMustNotContain);
		this.setLocationMustContain(locationMustContain);
		this.setLocationMustNotContain(locationMustNotContain);
		this.setDescriptionMustContain(descriptionMustContain);
		this.setDescriptionMustNotContain(descriptionMustNotContain);
		this.setAccessLevels(accessLevels);
		this.setAttendeeStatuses(attendeeStatuses);
		this.setAvailabilities(availabilities);
		this.setEventStatuses(eventStatuses);
		this.setWeekdays(weekdays);

		// Detail settings
		this.setCloneTitle(cloneTitle);
		this.setCustomTitle(customTitle);
		this.setCloneLocation(cloneLocation);
		this.setCustomLocation(customLocation);
		this.setCloneDescription(cloneDescription);
		this.setCustomDescription(customDescription);
		this.setCloneSelfAttendeeStatus(cloneSelfAttendeeStatus);
		this.setCloneSelfAttendeeStatusReverse(cloneSelfAttendeeStatusReverse);
		this.setSelfAttendeeName(selfAttendeeName);
		this.setReserveBefore(reserveBefore);
		this.setReserveAfter(reserveAfter);
		this.setCustomAccessLevel(accessLevelOverride);
		this.setCustomAvailability(availabilityOverride);

		// Attendees
		this.setCloneAttendees(cloneAttendees);
		this.setUseDummyEmailAddresses(useDummyEmailAddresses);
		this.setDummyEmailDomain(dummyEmailDomain);
		this.setAttendeesAsText(attendeesAsText);
		this.setCustomAttendee(customAttendee);
		this.setCustomAttendeeName(customAttendeeName);
		this.setCustomAttendeeEmail(customAttendeeEmail);

		// Reminders
		this.setCloneReminders(cloneReminders);
		this.setCustomReminder(customReminder);
		this.setCustomReminderMethod(customReminderMethod);
		this.setCustomReminderMinutes(customReminderMinutes);

		// Additional parameters
		this.setRetainClonesOutsideSourceEventWindow(retainClonesOutsideSourceEventWindow);

		// Advanced settings
		this.setHash(hashMethod, hash);
	}

	public String getId() {
		return mId;
	}

	public String getHash() {
		return mHash;
	}

	public int getVersion() {
		return CURRENT_RULE_VERSION;
	}

	public boolean isEnabled() {
		return mEnabled;
	}

	public String getName() {
		return mName;
	}

	public int getMethod() {
		return mMethod;
	}

	public String getDstCalendarRef() {
		return mDstCalendarRef;
	}

	public String getSrcCalendarRef() {
		return mSrcCalendarRef;
	}

	public String getSrcCalendarHash() {
		return mSrcCalendarHash;
	}

	public boolean isReadOnly() {
		return mReadOnly;
	}

	public boolean getIncludeClones() {
		return mIncludeClones;
	}

	public boolean useEventFilters() {
		return mUseEventFilters;
	}

	public int getEventTypeFilter() {
		return mEventTypeFilter;
	}

	public String getTitleMustContain() {
		return mTitleMustContain;
	}

	public String getTitleMustNotContain() {
		return mTitleMustNotContain;
	}

	public String getLocationMustContain() {
		return mLocationMustContain;
	}

	public String getLocationMustNotContain() {
		return mLocationMustNotContain;
	}

	public String getDescriptionMustContain() {
		return mDescriptionMustContain;
	}

	public String getDescriptionMustNotContain() {
		return mDescriptionMustNotContain;
	}

	public AccessLevels getAccessLevels() {
		return mAccessLevels;
	}

	public AttendeeStatuses getAttendeeStatuses() {
		return mAttendeeStatuses;
	}

	public Availabilities getAvailabilities() {
		return mAvailabilities;
	}

	public EventStatuses getEventStatuses() {
		return mEventStatuses;
	}

	public Weekdays getWeekdays() {
		return mWeekdays;
	}

	public boolean getCloneTitle() {
		return mCloneTitle;
	}

	public String getCustomTitle() {
		return mCustomTitle;
	}

	public boolean getCloneLocation() {
		return mCloneLocation;
	}

	public String getCustomLocation() {
		return mCustomLocation;
	}

	public boolean getCloneDescription() {
		return mCloneDescription;
	}

	public String getCustomDescription() {
		return mCustomDescription;
	}

	public boolean getCloneSelfAttendeeStatus() {
		return mCloneSelfAttendeeStatus;
	}

	public boolean getCloneSelfAttendeeStatusReverse() {
		return mCloneSelfAttendeeStatusReverse;
	}

	public String getSelfAttendeeName() {
		return mSelfAttendeeName;
	}

	public long getSyncPeriodBefore() {
		return mSyncPeriodBefore;
	}

	public long getSyncPeriodAfter() {
		return mSyncPeriodAfter;
	}

	public int getReserveBefore() {
		return mReserveBefore;
	}

	public int getReserveAfter() {
		return mReserveAfter;
	}

	public int getCustomAccessLevel() {
		return mCustomAccessLevel;
	}

	public int getCustomAvailability() {
		return mCustomAvailability;
	}

	public boolean getCloneAttendees() {
		return mCloneAttendees;
	}

	public boolean getUseDummyEmailAddresses() {
		return mUseDummyEmailAddresses;
	}

	public String getDummyEmailDomain() {
		return mDummyEmailDomain;
	}

	public boolean getAttendeesAsText() {
		return mAttendeesAsText;
	}

	public boolean getCustomAttendee() {
		return mCustomAttendee;
	}

	public String getCustomAttendeeName() {
		return mCustomAttendeeName;
	}

	public String getCustomAttendeeEmail() {
		return mCustomAttendeeEmail;
	}

	public boolean getCloneReminders() {
		return mCloneReminders;
	}

	public boolean getCustomReminder() {
		return mCustomReminder;
	}

	public int getCustomReminderMethod() {
		return mCustomReminderMethod;
	}

	public int getCustomReminderMinutes() {
		return mCustomReminderMinutes;
	}

	public boolean getRetainClonesOutsideSourceEventWindow() {
		return mRetainClonesOutsideSourceEventWindow;
	}

	public int getHashMethod() {
		return mHashMethod;
	}

	public synchronized int tryLock() {
		if (mLocked == 0) {
			mLocked = LOCK_SECRET;
			return mLocked;
		}
		return 0;
	}

	public synchronized boolean tryRelease(int secret) {
		if (secret == LOCK_SECRET) {
			mLocked = 0;
			return true;
		}
		return false;
	}

	public boolean isExecuting() {
		return mExecuting;
	}

	public boolean hasExecuted() {
		return mExecuted;
	}

	public int getStatus() {
		return mStatus;
	}

	public boolean isDirty() {
		return mIsDirty;
	}

	public ClonerLog getLog() {
		return mLog;
	}

	public String getSummary() {
		return mSummary;
	}

	public void setLog(ClonerLog log) {
		mLog = log;
	}

	public void startExecution(String summary) {
		mExecuting = true;
		mSummary = summary;
	}

	public void finishExecution(int status, String summary) {
		mExecuting = false;
		mExecuted = true;
		mStatus = status;
		mSummary = summary;
		mIsDirty = false;
	}

	public void markDirty() {
		mIsDirty = true;
	}

	private void validateConsistency() {
		// This method is called after each setter

		// Correct impossible values for Samsung devices
		if (mCustomAvailability == Rule.CUSTOM_AVAILABILITY_OUT_OF_OFFICE) {
			// Check if the destination calendar supports OUT_OF_OFFICE
			CalendarLoader.CalendarInfo info = CalendarLoader.getCalendarByRef(mDstCalendarRef);
			if (info.getCalendar() != null
					&& !Device.Samsung.supportsAvailabilitySamsung(info.getCalendar().getAccountType())) {
				mCustomAvailability = Rule.CUSTOM_AVAILABILITY_SOURCE;
			}
		}

		// Correct impossible values for AGGREGATE rules
		if (mMethod == METHOD_AGGREGATE) {
			// Selection options
			if (mSyncPeriodBefore > MAX_AGGREGATE_RULE_PERIOD) {
				mSyncPeriodBefore = MAX_AGGREGATE_RULE_PERIOD;
			}
			if (mSyncPeriodAfter > MAX_AGGREGATE_RULE_PERIOD) {
				mSyncPeriodAfter = MAX_AGGREGATE_RULE_PERIOD;
			}
			// Override options
			mCloneTitle = false;
			mCloneLocation = false;
			mCloneDescription = false;
			mCloneSelfAttendeeStatus = false;
			if (mCustomAccessLevel == Rule.CUSTOM_ACCESS_LEVEL_SOURCE) {
				mCustomAccessLevel = Rule.CUSTOM_ACCESS_LEVEL_DEFAULT;
			}
			if (mCustomAvailability == Rule.CUSTOM_AVAILABILITY_SOURCE) {
				mCustomAvailability = Rule.CUSTOM_AVAILABILITY_BUSY;
			}
			// Guest list options
			mCloneAttendees = false;
			mAttendeesAsText = false;
			// Reminder options
			mCloneReminders = false;
		}
	}

	private void setId(String id) {
		if (id.contentEquals("")) {
			id = UUID.randomUUID().toString();
		}
		mId = id;
		this.setHash(mHashMethod, mHash);
		this.validateConsistency();
	}

	public void setEnabled(boolean enabled) {
		mEnabled = enabled;
		this.validateConsistency();
	}

	public void setName(String name) {
		mName = name;
		this.validateConsistency();
	}

	public void setMethod(int method) {
		mMethod = ClonerVersion.setRuleMethod(method);
		this.validateConsistency();
	}

	public void clearSrcCalendarRef() {
		mSrcCalendarRef = "";
		this.validateConsistency();
	}

	public void clearDstCalendarRef() {
		mDstCalendarRef = "";
		this.validateConsistency();
	}

	public void setDstCalendarRef(String ref) {
		mDstCalendarRef = ref;
		this.validateConsistency();
	}

	public void setSrcCalendarRef(String ref) {
		if (!mSrcCalendarRef.contentEquals(ref)) {
			// Update source calendar reference
			mSrcCalendarRef = ref;
			// Calculate new hash
			this.setHash(mHashMethod, mHash);
			// Reset source calendar hash
			this.clearSrcCalendarHash();
			this.validateConsistency();
		}
	}

	public void clearSrcCalendarHash() {
		mSrcCalendarHash = "";
		this.validateConsistency();
	}

	public void setReadOnly(boolean readOnly) {
		mReadOnly = readOnly;
	}

	public boolean setSyncPeriodBefore(long periodBefore) {
		mSyncPeriodBefore = Utilities.getSelectionFromArray(SYNC_PERIODS, periodBefore, SYNC_PERIODS[0]);
		this.validateConsistency();
		return mSyncPeriodBefore == periodBefore;
	}

	public boolean setSyncPeriodAfter(long periodAfter) {
		mSyncPeriodAfter = Utilities.getSelectionFromArray(SYNC_PERIODS, periodAfter, SYNC_PERIODS[0]);
		this.validateConsistency();
		return mSyncPeriodAfter == periodAfter;
	}

	public void setIncludeClones(boolean enabled) {
		mIncludeClones = ClonerVersion.setIncludeClones(enabled);
		this.validateConsistency();
	}

	public void setUseEventFilters(boolean enabled) {
		mUseEventFilters = ClonerVersion.setUseEventFilters(enabled);
		this.validateConsistency();
	}

	public void setEventTypeFilter(int eventTypeFilter) {
		mEventTypeFilter = eventTypeFilter;
		this.validateConsistency();
	}

	public void setTitleMustContain(String titleMustContain) {
		mTitleMustContain = titleMustContain;
		this.validateConsistency();
	}

	public void setTitleMustNotContain(String titleMustNotContain) {
		mTitleMustNotContain = titleMustNotContain;
		this.validateConsistency();
	}

	public void setLocationMustContain(String locationMustContain) {
		mLocationMustContain = locationMustContain;
		this.validateConsistency();
	}

	public void setLocationMustNotContain(String locationMustNotContain) {
		mLocationMustNotContain = locationMustNotContain;
		this.validateConsistency();
	}

	public void setDescriptionMustContain(String descriptionMustContain) {
		mDescriptionMustContain = descriptionMustContain;
		this.validateConsistency();
	}

	public void setDescriptionMustNotContain(String descriptionMustNotContain) {
		mDescriptionMustNotContain = descriptionMustNotContain;
		this.validateConsistency();
	}

	public void setAccessLevels(AccessLevels accessLevels) {
		mAccessLevels = accessLevels.clone();
		this.validateConsistency();
	}

	public void setAttendeeStatuses(AttendeeStatuses attendeeStatuses) {
		mAttendeeStatuses = attendeeStatuses.clone();
		this.validateConsistency();
	}

	public void setAvailabilities(Availabilities availabilities) {
		mAvailabilities = availabilities.clone();
		this.validateConsistency();
	}

	public void setEventStatuses(EventStatuses eventStatuses) {
		mEventStatuses = eventStatuses.clone();
		this.validateConsistency();
	}

	public void setWeekdays(Weekdays weekdays) {
		mWeekdays = weekdays.clone();
		this.validateConsistency();
	}

	public void setCloneTitle(boolean enabled) {
		mCloneTitle = enabled;
		this.validateConsistency();
	}

	public void setCustomTitle(String title) {
		mCustomTitle = title;
		this.validateConsistency();
	}

	public void setCloneLocation(boolean enabled) {
		mCloneLocation = enabled;
		this.validateConsistency();
	}

	public void setCustomLocation(String location) {
		mCustomLocation = location;
		this.validateConsistency();
	}

	public void setCloneDescription(boolean enabled) {
		mCloneDescription = enabled;
		this.validateConsistency();
	}

	public void setCustomDescription(String description) {
		mCustomDescription = description;
		this.validateConsistency();
	}

	public void setCloneSelfAttendeeStatus(boolean enabled) {
		mCloneSelfAttendeeStatus = ClonerVersion.setCloneSelfAttendeeStatus(enabled);
		this.validateConsistency();
	}

	public void setCloneSelfAttendeeStatusReverse(boolean enabled) {
		mCloneSelfAttendeeStatusReverse = enabled;
		this.validateConsistency();
	}

	public void setSelfAttendeeName(String name) {
		mSelfAttendeeName = name;
		this.validateConsistency();
	}

	public void setReserveBefore(int reserveBefore) {
		mReserveBefore = reserveBefore;
		this.validateConsistency();
	}

	public void setReserveAfter(int reserveAfter) {
		mReserveAfter = reserveAfter;
		this.validateConsistency();
	}

	public void setCustomAccessLevel(int customAccessLevel) {
		mCustomAccessLevel = ClonerVersion.setCustomAccessLevel(customAccessLevel);
		this.validateConsistency();
	}

	public void setCustomAvailability(int customAvailability) {
		mCustomAvailability = ClonerVersion.setCustomAvailability(customAvailability);
		this.validateConsistency();
	}

	public void setCloneAttendees(boolean enabled) {
		mCloneAttendees = ClonerVersion.setCloneAttendees(enabled);
		this.validateConsistency();
	}

	public void setUseDummyEmailAddresses(boolean enabled) {
		mUseDummyEmailAddresses = enabled;
		this.validateConsistency();
	}

	public void setDummyEmailDomain(String domain) {
		if (BLACKHOLE_DOMAIN.contentEquals(domain)) {
			mDummyEmailDomain = BLACKHOLE_DOMAIN;
		} else {
			mDummyEmailDomain = DEFAULT_DUMMY_DOMAIN;
		}
		this.validateConsistency();
	}

	public void setAttendeesAsText(boolean enabled) {
		mAttendeesAsText = ClonerVersion.setAttendeesAsText(enabled);
		this.validateConsistency();
	}

	public void setCustomAttendee(boolean enabled) {
		mCustomAttendee = ClonerVersion.setCustomAttendee(enabled);
		this.validateConsistency();
	}

	public void setCustomAttendeeName(String name) {
		mCustomAttendeeName = name;
		this.validateConsistency();
	}

	public void setCustomAttendeeEmail(String email) {
		mCustomAttendeeEmail = email;
		this.validateConsistency();
	}

	public void setCloneReminders(boolean enabled) {
		mCloneReminders = ClonerVersion.setCloneReminders(enabled);
		this.validateConsistency();
	}

	public void setCustomReminder(boolean enabled) {
		mCustomReminder = ClonerVersion.setCustomReminder(enabled);
		this.validateConsistency();
	}

	public void setCustomReminderMethod(int method) {
		mCustomReminderMethod = method;
		this.validateConsistency();
	}

	public void setCustomReminderMinutes(int minutes) {
		mCustomReminderMinutes = minutes;
		this.validateConsistency();
	}

	public void setRetainClonesOutsideSourceEventWindow(boolean enabled) {
		mRetainClonesOutsideSourceEventWindow = ClonerVersion.setRetainClonesOutsideSourceEventWindow(enabled);
		this.validateConsistency();
	}

	public void setHash(int hashMethod, String hash) {
		if (hashMethod == HASH_METHOD_MANUAL) {
			if (hash != null && hash.length() == 32) {
				Pattern pattern = Pattern.compile(EventMarker.HASH_PATTERN);
				Matcher m = pattern.matcher(hash);
				if (!m.find()) {
					hashMethod = HASH_METHOD_SOURCE_CALENDAR;
				}
			} else {
				hashMethod = HASH_METHOD_SOURCE_CALENDAR;
			}
		}

		mHashMethod = hashMethod;
		switch (hashMethod) {
		case HASH_METHOD_SOURCE_CALENDAR:
			mHash = Hasher.hash(mSrcCalendarRef);
			break;
		case HASH_METHOD_RULE_ID:
			mHash = Hasher.hash(mId);
			break;
		case HASH_METHOD_MANUAL:
			mHash = hash;
			break;
		}
		this.validateConsistency();
	}

	public void setHash(String hash) {
		if (mHashMethod == HASH_METHOD_MANUAL) {
			mHash = hash;
		}
	}

	@Override
	public String toString() {
		return mName;
	}
}
