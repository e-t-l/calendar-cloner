package com.dizzl.android.CalendarCloner;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;

class EventCloner extends EventProcessor {
	private final long ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
	protected final EventsTable mEventsTable;
	private AttendeeCloner mAttendeeCloner = null;
	private ReminderCloner mReminderCloner = null;
	protected EventDiffer mDiffer;
	private final CloneIdIndex mValidClones = new CloneIdIndex();
	private final CloneIndex mExistingClones;

	protected Period mSrcSingleEventWindow0 = new Period(new DateTime().withTime(0, 0, 0, 0),
		new DateTime().plusDays(1).withTime(0, 0, 0, 0));
	protected Period mSrcSingleEventWindow = new Period(new DateTime().minusDays(1).withTime(0, 0, 0, 0),
		new DateTime().plusDays(1).withTime(0, 0, 0, 0));
	
		
	protected static final int RESULT_EVENT_UNCHANGED = -1;
	protected static final int RESULT_EVENT_INSERTED = -2;
	protected static final int RESULT_EVENT_UPDATED = -3;
	protected static final int RESULT_EVENT_ERROR = -4;

	protected class PartialCloneResult {
		int result = RESULT_EVENT_UNCHANGED;
		long cloneId = 0;
		boolean completed = true;
	}

	protected class EventContext {
		private long mOriginalId = 0;
		private final AttendeeCloner.AttendeeCloneContext mAttendeeCloneContext = new AttendeeCloner.AttendeeCloneContext();
		private final ReminderCloner.ReminderCloneContext mReminderCloneContext = new ReminderCloner.ReminderCloneContext();
	}

	protected class CloneResult extends EventResult {
		long cloneId = 0;
	}

	public EventCloner(Rule rule) {
		super(rule);
		ClonerDb db = ClonerApp.getDb(rule.isReadOnly());
		mEventsTable = new EventsTable(db);
		mExistingClones = new CloneIndex(mEventsTable);
	}

	protected List<Event> getExceptions(Event event) {
		// Set up the result list
		LinkedList<Event> result = new LinkedList<Event>();

		// Query the database for all event exceptions
		Cursor exceptionCur = mEventsTable.query("((" + Events.ORIGINAL_ID + "=?))",
				new String[] { "" + event.getId() }, Events.ORIGINAL_INSTANCE_TIME + " ASC, " + Events._ID + " ASC");
		if (exceptionCur != null) {
			try {
				DateTime lastOriginalInstanceTime = null;
				while (exceptionCur.moveToNext()) {
					DbEvent exception = new DbEvent(mEventsTable, new DbObject(exceptionCur));
					// If the exception was not marked deleted
					if (!exception.isDeleted()) {
						// Skip events which have the same original instance
						// time
						// (Technically a db issue, but solve it here by
						// skipping duplicates)
						if (lastOriginalInstanceTime == null
								|| exception.getOriginalInstanceTime() != lastOriginalInstanceTime) {
							// Remember this exception's original instance time
							lastOriginalInstanceTime = exception.getOriginalInstanceTime();
							exception.loadAll();
							result.add(exception);
						}
					}
				}
			} finally {
				exceptionCur.close();
			}
		}
		return result;
	}

	protected void registerValidClone(Event event, long cloneId) {
		if (event.isSingleEvent()) {
			// Merge source single event window with event's window
			mSrcSingleEventWindow.merge(event.getPeriod());
			mSrcSingleEventWindow0.merge(event.getPeriod());
		}
		// Add clone id to list of valid clone ids
		mValidClones.add(event, cloneId);
	}

	private boolean setEventSelfAttendeeStatus(Event event, int status, AttendeeDeltas deltas) {
		// Figure out the attendee status of the original event
		int originalStatus = event.getSelfAttendeeStatus();
		if (!DbAttendee.attendeeStatusIsAResponse(originalStatus)) {
			originalStatus = Attendees.ATTENDEE_STATUS_INVITED;
		}
		// And prepare the status to set
		if (!DbAttendee.attendeeStatusIsAResponse(status)) {
			status = Attendees.ATTENDEE_STATUS_INVITED;
		}

		// If the status is a response, and the status differs from the
		// selfAttendeeStatus of the event, update the latter
		if (DbAttendee.attendeeStatusIsAResponse(status) && status != originalStatus) {
			// Update the original event's status
			DbAttendee eventAttendee = DbAttendee.getByEmail(mAttendeesTable, event.getId(),
					mSrcCalendar.getOwnerAccount());
			// If the original event does not have the calendar owner
			// listed as an explicit attendee (group invites), add him/her,
			// otherwise simply update the attendee status
			if (eventAttendee == null) {
				// Add yourself as attendee
				ContentValues delta = new ContentValues();
				delta.put(Attendees.ATTENDEE_EMAIL, mSrcCalendar.getOwnerAccount());
				delta.put(Attendees.ATTENDEE_NAME, mRule.getSelfAttendeeName());
				delta.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
				delta.put(Attendees.ATTENDEE_STATUS, status);
				delta.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED);
				delta.put(Attendees.EVENT_ID, event.getId());
				mAttendeesTable.insert(delta);
				deltas.put(mRule.getSelfAttendeeName(), mSrcCalendar.getOwnerAccount(), delta);
				this.log(
						ClonerLog.LOG_UPDATE,
						ClonerApp.translate(R.string.cloner_log_original_attendee_added,
								new String[] { mSrcCalendar.getOwnerAccount() }));
			} else {
				// Update the original attendee's status
				ContentValues delta = new ContentValues();
				delta.put(Attendees.ATTENDEE_STATUS, status);
				mAttendeesTable.update(eventAttendee.getId(), delta);
				deltas.put(eventAttendee.getName(), eventAttendee.getEmail(), delta);
			}
			this.log(
					ClonerLog.LOG_UPDATE,
					ClonerApp.translate(
							R.string.cloner_log_attendee_status_updated,
							new String[] { mSrcCalendar.getOwnerAccount(),
									new AttendeeStatuses(true).getKeyName(status), mSrcCalendar.getDisplayName() }));
			return true;
		}

		return false;
	}

	private boolean setSelfAttendeeToStatusNone(DbAttendee cloneAttendee, Event event, AttendeeDeltas deltas) {
		// If the attendee hasn't responded explicitly, set him to STATUS_NONE
		if (cloneAttendee != null && cloneAttendee.getStatus() == Attendees.ATTENDEE_STATUS_INVITED) {
			ContentValues delta = new ContentValues();
			int status = Attendees.ATTENDEE_STATUS_NONE;
			delta.put(Attendees.ATTENDEE_STATUS, status);
			mAttendeesTable.update(cloneAttendee.getId(), delta);
			deltas.put(cloneAttendee.getName(), cloneAttendee.getEmail(), delta);
			this.log(
					ClonerLog.LOG_UPDATE,
					ClonerApp.translate(
							R.string.cloner_log_attendee_status_updated_automatically,
							new String[] { mDstCalendar.getOwnerAccount(),
									new AttendeeStatuses(true).getKeyName(status), mDstCalendar.getDisplayName() }));
			return true;
		}

		return false;
	}

	public boolean cloneSelfAttendeeStatus(Event event, Event clone, AttendeeDeltas deltas) {
		// Return true if attendees or statuses were updated
		boolean updated = false;

		// Figure out if the event is older than a day
		DateTime now = new DateTime();
		Period eventPeriod = event.getPeriod();
		boolean eventFromDistantPast = eventPeriod.endsBefore(now) && eventPeriod.distanceTo(now) >= ONE_DAY_IN_MILLIS;

		// Check if we are the organizer of the event
		boolean selfOrganizer = event.getOrganizer() != null ? event.getOrganizer().contentEquals(
				mSrcCalendar.getOwnerAccount()) : true;
		String clonedSelfEmail = mDstCalendar.getOwnerAccount();

		// Get the cloned SELF attendee
		DbAttendee clonedAttendee = DbAttendee.getByEmail(mAttendeesTable, clone.getId(), clonedSelfEmail);

		// Exit if there is no cloned SELF
		if (clonedAttendee == null) {
			return updated;
		}

		// If we're not the organizer
		if (!selfOrganizer) {
			// The event is eligible for an attendee status update if and only
			// if the event is in the future or has only recently passed
			if (!eventFromDistantPast) {
				// Now copy the status of the destination calendar owner to the
				// source event
				updated = this.setEventSelfAttendeeStatus(event, clonedAttendee.getStatus(), deltas) || updated;
			} else {
				// Set all events from the distant past to NONE if the user
				// did not already respond, but without touching the source
				// event
				updated = this.setSelfAttendeeToStatusNone(clonedAttendee, event, deltas) || updated;
			}
		}
		return updated;
	}

	private EventResult cloneAttendees(Event event, Event clone, boolean resetCloneSelfAttendeeStatus,
			EventContext eventContext, boolean addEmptyLine) {
		EventResult result = new EventResult();

		// Initialize the proper context for cloning attendees
		AttendeeCloner.AttendeeCloneContext attCloneContext = eventContext != null ? eventContext.mAttendeeCloneContext
				: new AttendeeCloner.AttendeeCloneContext();
		attCloneContext.logLines = this.getLogLines();

		// Clone the guest list
		AttendeeCloner.AttendeeCloneResult attendeeResult = mAttendeeCloner.process(event, clone.getId(),
				resetCloneSelfAttendeeStatus, attCloneContext);
		result.completed &= attendeeResult.completed;

		if (attendeeResult.updateCount > 0) {
			result.updateCount = 1;
		}

		// Check if the attendee statuses of the event and its clone should be
		// synced
		if (mRule.getCloneSelfAttendeeStatusReverse()
				&& this.cloneSelfAttendeeStatus(event, clone, attendeeResult.deltas)) {
			result.updateCount = 1;
		}

		if (result.updateCount > 0) {
			if (addEmptyLine) {
				this.log(ClonerLog.LOG_UPDATE, "");
			}
			// Log the updated guest list
			this.log(ClonerLog.LOG_UPDATE, ClonerApp.translate(R.string.cloner_log_attendees_updated));
			// Only pass along deltas if we're in readonly mode
			AttendeeDeltas deltas = mAttendeesTable.getDb().isReadOnly() ? attendeeResult.deltas : null;
			this.logAttendees(DbAttendee.getByEvent(mAttendeesTable, event.getId(), deltas),
					DbAttendee.getByEvent(mAttendeesTable, clone.getId(), deltas), mSrcCalendar, mDstCalendar,
					attendeeResult.deltas, mRule.getDummyEmailDomain());
		} else {
			// Message only makes sense if we wanted to clone the attendees
			if (mRule.getCloneAttendees()) {
				if (addEmptyLine) {
					this.log(ClonerLog.LOG_UPDATE, "");
				}
				this.log(ClonerLog.LOG_INFO, ClonerApp.translate(R.string.cloner_log_attendees_unchanged));
			}
		}

		return result;
	}

	private boolean cloneReminders(Event event, long cloneId, EventContext eventContext, boolean addEmptyLine) {
		// Initialize the proper context for cloning reminders
		ReminderCloner.ReminderCloneContext remCloneContext = eventContext != null ? eventContext.mReminderCloneContext
				: new ReminderCloner.ReminderCloneContext();
		remCloneContext.logLines = this.getLogLines();

		// Clone the necessary reminders
		boolean updated = mReminderCloner.process(event, cloneId, remCloneContext);

		if (updated) {
			if (addEmptyLine) {
				this.log(ClonerLog.LOG_UPDATE, "");
			}
			this.log(ClonerLog.LOG_UPDATE, ClonerApp.translate(R.string.cloner_log_reminders_updated));
			// Only pass along reminder deltas if we're in readonly mode
			Map<Long, ContentValues> deltas = mRemindersTable.getDb().isReadOnly() ? remCloneContext.reminderDeltas
					: null;
			this.logReminders(ClonerLog.LOG_UPDATE, DbReminder.getReminders(mRemindersTable, event.getId(), deltas),
					DbReminder.getReminders(mRemindersTable, cloneId, deltas), remCloneContext.mappedReminders);
		}

		return updated;
	}

	private boolean compareEventExcludeFields(Event event, Event clone, String[] fields) {
		ContentValues delta = new ContentValues();
		mDiffer.compare(new ClonedEvent(event, mRule), clone, delta);
		if (fields != null) {
			for (int i = 0; i < fields.length; i++) {
				delta.remove(fields[i]);
			}
		}
		return delta.size() == 0;
	}

	private boolean canReuseClone(Event event, Event clone) {
		// Don't reuse if the clone is not owned by the destination calendar
		if (clone.getOrganizer() != null && !clone.getOrganizer().contentEquals(mDstCalendar.getOwnerAccount())) {
			return false;
		}

		// Don't reuse if deleted
		if (clone.isDeleted()) {
			return false;
		}

		// Events can never change type, so don't reuse if this is the case
		if (event.isRecurringEvent() != clone.isRecurringEvent()
				|| event.isRecurringEventException() != clone.isRecurringEventException()) {
			return false;
		}

		if (event.isRecurringEvent()) {
			// Compare the event. If something has changed, don't reuse
			if (!this.canReuseRecurringClone(event, clone)) {
				return false;
			}
		}

		return true;
	}

	// Returns true if the structure of the recurring event is similar
	private boolean canReuseRecurringClone(Event event, Event clone) {
		String[] fields = new String[] { Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION };
		// First compare the event and its clone
		if (!compareEventExcludeFields(event, clone, fields)) {
			// If not similar, don't reuse (safety)
			return false;
		}

		// Then compare all exceptions to all exception clones
		List<Event> exceptions = this.getExceptions(event);
		for (Event exception : exceptions) {
			Event exceptionClone = mExistingClones.getCloneOf(exception);
			if (exceptionClone == null) {
				// Inserting new exceptions proves tricky! So if we need to
				// add/remove one, recreate the entire recurring event
				return false;
			}
			if (!this.canReuseClone(exception, exceptionClone)
					|| !this.canReuseExceptionClone(exception, exceptionClone, clone.getId())) {
				// We can't reuse the cloned exception
				return false;
			}
			if (!compareEventExcludeFields(exception, exceptionClone, fields)) {
				// If not similar, don't reuse (safety)
				return false;
			}
		}

		return true;
	}

	private boolean canReuseExceptionClone(Event exception, Event exceptionClone, long cloneOriginalId) {
		// Only reuse the exception clone if original id matches
		return exceptionClone.getOriginalID() == cloneOriginalId;
	}

	protected boolean canModifyEvent(EventContext eventContext) {
		if ((eventContext == null || eventContext.mOriginalId == 0) && !Limits.canModify(Limits.TYPE_EVENT)) {
			return false;
		}
		return true;
	}

	private boolean deleteEvent(Event event) {
		if (event.isRecurringEventException()) {
			if (mEventsTable.delete(event.getOriginalID()) != 1) {
				return false;
			}
		}
		return mEventsTable.delete(event.getId()) == 1;
	}

	private PartialCloneResult cloneEvent(final Event event, Event clone, EventContext eventContext) {
		// Keep track of whether we updated the cloned event
		PartialCloneResult result = new PartialCloneResult();

		boolean eventWasRescheduled = false;
		final ContentValues delta = new ContentValues();
		if (clone != null) {
			// Find out if we can reuse the clone
			if (!this.canReuseClone(event, clone)) {
				// We can not reuse the clone, so delete it first so it doesn't
				// interfere with the rest of the process
				if (!this.canModifyEvent(eventContext)) {
					this.log(ClonerLog.LOG_WARNING, ClonerApp.translate(R.string.cloner_log_event_limit_reached));
					result.completed = false;
					return result;
				}

				// Delete the unreusable clone
				if (!mRule.isReadOnly() && !this.deleteEvent(clone)) {
					this.log(ClonerLog.LOG_ERROR, ClonerApp.translate(R.string.error_processing_event));
					result.result = RESULT_EVENT_ERROR;
					return result;
				}

				this.log(ClonerLog.LOG_UPDATE, ClonerApp.translate(R.string.cloner_log_old_clone_deleted));

				// Create a new empty clone and reclone all fields
				clone = new DbEvent(mEventsTable, mDstCalendar);
			} else {
				// Check if the event was rescheduled (requires user status
				// reset later)
				eventWasRescheduled = mDiffer.eventWasRescheduled(new ClonedEvent(event, mRule), clone);
			}
		} else {
			// If we don't reuse an existing clone, create an empty one
			clone = new DbEvent(mEventsTable, mDstCalendar);
		}

		result.cloneId = clone.getId();

		// Calculate the delta between the event and existing clone
		mDiffer.compare(new ClonedEvent(event, mRule), clone, delta);
		// Only save when some values were changed
		if (delta.size() > 0) {
			// If there was a clone already, update the clone. Otherwise insert
			// a new event with the given data
			if (clone.getId() > 0) {
				// Check to see if we can update within limits
				if (!this.canModifyEvent(eventContext)) {
					this.log(ClonerLog.LOG_WARNING, ClonerApp.translate(R.string.cloner_log_event_limit_reached));
					result.completed = false;
					return result;
				}

				// Update the clone's fields
				if (!mRule.isReadOnly() && mEventsTable.update(clone.getId(), delta) != 1) {
					this.log(ClonerLog.LOG_ERROR, ClonerApp.translate(R.string.error_processing_event));
					result.result = RESULT_EVENT_ERROR;
					return result;
				}

				result.result = RESULT_EVENT_UPDATED;
			} else {
				// Check to see if we can insert within limits
				if (!this.canModifyEvent(eventContext)) {
					this.log(ClonerLog.LOG_WARNING, ClonerApp.translate(R.string.cloner_log_event_limit_reached));
					result.completed = false;
					return result;
				}

				// Insert the new clone into the destination calendar
				delta.put(Events.CALENDAR_ID, mDstCalendar.getId());
				if (eventContext != null && eventContext.mOriginalId > 0) {
					delta.put(Events.ORIGINAL_ID, eventContext.mOriginalId);
				}
				result.cloneId = mEventsTable.insert(delta);
				if (!mRule.isReadOnly() && result.cloneId <= 0) {
					this.log(ClonerLog.LOG_ERROR, ClonerApp.translate(R.string.error_processing_event));
					result.result = RESULT_EVENT_ERROR;
					return result;
				}

				result.result = RESULT_EVENT_INSERTED;
			}

			// Reload clone or fake its change with a DbEventWithDelta instance
			if (mEventsTable.getDb().isReadOnly()) {
				clone = DbEvent.get(mEventsTable, result.cloneId, delta);
			} else {
				clone = DbEvent.get(mEventsTable, result.cloneId);
			}
			this.logEvents(event, clone, delta);
		}

		// Add clone's id to list of valid clones
		this.registerValidClone(event, result.cloneId);

		// Clone attendees
		EventResult attCloneResult = this.cloneAttendees(event, clone, eventWasRescheduled, eventContext,
				result.result != RESULT_EVENT_UNCHANGED);
		result.completed &= attCloneResult.completed;
		if (attCloneResult.updateCount > 0) {
			if (result.result != RESULT_EVENT_INSERTED) {
				result.result = RESULT_EVENT_UPDATED;
			}
		}

		// Clone reminders
		if (this.cloneReminders(event, result.cloneId, eventContext, result.result != RESULT_EVENT_UNCHANGED)) {
			if (result.result != RESULT_EVENT_INSERTED) {
				result.result = RESULT_EVENT_UPDATED;
			}
		}

		return result;
	}

	@Override
	protected void skipSingleEvent(Event event) {
		super.skipSingleEvent(event);
		// The original event can not be cloned at this time, so add its known
		// clone to the list of valid clones so we don't delete it later
		Event clone = mExistingClones.getCloneOf(event);
		if (clone != null && clone.getId() > 0) {
			this.registerValidClone(event, clone.getId());
		}
	}

	@Override
	protected void skipRecurringEvent(Event event) {
		super.skipRecurringEvent(event);
		// The original event can not be cloned at this time, so add its known
		// clone to the list of valid clones so we
		// don't delete it later
		Event clone = mExistingClones.getCloneOf(event);
		this.registerValidClone(event, clone != null ? clone.getId() : 0);

		// Register all exceptions
		List<Event> exceptions = this.getExceptions(event);
		for (Event exception : exceptions) {
			Event exceptionClone = mExistingClones.getCloneOf(exception);
			this.registerValidClone(exception, exceptionClone != null ? exceptionClone.getId() : 0);
		}
	}

	protected void logCloneSummary(Event event, PartialCloneResult cloneResult, EventResult result) {
		if (result.completed) {
			switch (cloneResult.result) {
			case RESULT_EVENT_UNCHANGED:
				this.logSummary(ClonerLog.LOG_INFO, ClonerApp.translate(R.string.cloner_state_unchanged), event);
				break;
			case RESULT_EVENT_INSERTED:
				this.logSummary(ClonerLog.LOG_UPDATE, ClonerApp.translate(R.string.cloner_log_inserted), event);
				break;
			case RESULT_EVENT_UPDATED:
				this.logSummary(ClonerLog.LOG_UPDATE, ClonerApp.translate(R.string.cloner_log_updated), event);
				break;
			case RESULT_EVENT_ERROR:
				this.logSummary(ClonerLog.LOG_ERROR, ClonerApp.translate(R.string.error_processing_event), event);
			}
		} else {
			this.logSummary(ClonerLog.LOG_WARNING, ClonerApp.translate(R.string.cloner_log_limit_reached), event);
			result.completed = false;
		}
	}

	@Override
	protected EventResult createEventResult() {
		return new CloneResult();
	}

	@Override
	protected EventResult processSingleEvent(Event event) {
		// Keep track of whether we updated the cloned event
		CloneResult result = (CloneResult) super.processSingleEvent(event);
		PartialCloneResult mainCloneResult = this.cloneEvent(event, mExistingClones.getCloneOf(event), null);
		result.completed &= mainCloneResult.completed;
		if (mainCloneResult.result != RESULT_EVENT_UNCHANGED) {
			result.updateCount++;
		}
		result.cloneId = mainCloneResult.cloneId;
		this.logCloneSummary(event, mainCloneResult, result);
		return result;
	}

	@Override
	protected EventResult processRecurringEvent(Event event) {
		// Keep track of whether we updated the cloned event
		CloneResult result = (CloneResult) super.processRecurringEvent(event);
		// Initialize the differ to this event's duration
		mDiffer.setDefaultDuration(Duration.parseDuration(event.getDuration()));
		// Create an event context for this recurring event
		EventContext eventContext = new EventContext();
		// First clone the main recurring event itself
		PartialCloneResult mainCloneResult = this.cloneEvent(event, mExistingClones.getCloneOf(event), eventContext);
		result.completed &= mainCloneResult.completed;
		if (mainCloneResult.result != RESULT_EVENT_UNCHANGED) {
			result.updateCount++;
		}
		result.cloneId = mainCloneResult.cloneId;
		this.logCloneSummary(event, mainCloneResult, result);
		if (mainCloneResult.result == RESULT_EVENT_ERROR) {
			return result;
		}

		// Then clone all exceptions
		eventContext.mOriginalId = mainCloneResult.cloneId;
		int exceptionNr = 0;
		for (Event exception : this.getExceptions(event)) {
			// Start a new log entry
			this.startNewLogLines(this.getOriginalLogPrefix() + "." + ++exceptionNr);

			// Get the cloned exception event
			Event exceptionClone = mExistingClones.getCloneOf(exception);
			// Check if we can reuse this exception
			if (exceptionClone != null) {
				if (!this.canReuseClone(exception, exceptionClone)
						|| !this.canReuseExceptionClone(exception, exceptionClone, mainCloneResult.cloneId)) {
					exceptionClone = null;
				}
			}

			// Clone the exception
			PartialCloneResult exceptionCloneResult = this.cloneEvent(exception, exceptionClone, eventContext);
			result.completed &= exceptionCloneResult.completed;
			if (exceptionCloneResult.result != RESULT_EVENT_UNCHANGED) {
				result.updateCount++;
			}
			this.logCloneSummary(exception, exceptionCloneResult, result);
			if (exceptionCloneResult.result == RESULT_EVENT_ERROR) {
				return result;
			}
		}

		// Return true signals we updated clones
		return result;
	}

	@Override
	public InitResult init(ClonerLog log) {
		InitResult result = super.init(log);
		if (!result.isSuccess()) {
			return result;
		}

		mDiffer = new EventDiffer(mDstCalendar.getTimeZone());

		// Prepare index for clone matching
		CloneIndex.IndexResult indexResult = mExistingClones.index(mDstCalendar.getId(), mRule.getHash(),
				mRule.getSrcCalendarHash(), log);
		if (!indexResult.isSuccess()) {
			return new Processor.InitResult(false, indexResult.getErrorMessage(), result.getUpdateCount()
					+ indexResult.getUpdateCount());
		}
		// Set up an attendee cloner
		mAttendeeCloner = new AttendeeCloner(mRule, mSrcCalendar, mDstCalendar, mAttendeesTable);
		// Set up a reminder cloner
		mReminderCloner = new ReminderCloner(mRule, mRemindersTable, mDstCalendar.getMaxReminders());
		return new Processor.InitResult(true, "", result.getUpdateCount() + indexResult.getUpdateCount());
	}

	public Set<Long> getRemainingCloneIds() {
		// Return the list of unprocessed clones
		Set<Long> result = new HashSet<Long>();
		Set<Event> clones = mExistingClones.getAllClones();
		for (Event clone : clones) {
			if (!mValidClones.containsId(clone.getId())) {
				result.add(clone.getId());
			}
		}
		return result;
	}

	private boolean shouldDeleteClone(Event clone) {
		// Check if clone is a valid one, if not delete
		EventMarker.Marker marker = EventMarker.parseCloneEventHash(clone);
		if (marker != null) {
			if (mValidClones.containsHash(marker.eventHash)) {
				Long validCloneId = mValidClones.getCloneId(marker.eventHash);
				if (clone.getId() != validCloneId) {
					// Probably an overriden clone, so delete it
					return true;
				}
			}
		} else {
			// Should never reach here (invalidly marked as clone)
			return true;
		}

		// Check if we need to retain clone
		if (mRule.getRetainClonesOutsideSourceEventWindow()) {
			// Delete exceptions only if their parent event needs to be deleted
			if (clone.isRecurringEventException()) {
				Event parentEvent = DbEvent.get(mEventsTable, clone.getOriginalID());
				return parentEvent != null ? this.shouldDeleteClone(parentEvent) : true;
			}

			Period clonePeriod = clone.getPeriod();
			if (clone.isRecurringEvent()) {
				// Delete the recurring event if it overlaps with the event
				// window. Rationale is that the source event would have
				// overlapped with the event window too and therefore should
				// have existed.
				return clonePeriod.overlaps(mSrcSingleEventWindow);
			} //else {
			
			// For single events, we check if they start before or end after the event window
//TODO: alter or add condition for future events within sync period whose sources is deleted
				if (mRule.getSyncPeriodBefore() != 0) {
					return !clonePeriod.startsBeforeStartOf(mSrcSingleEventWindow)
							&& !clonePeriod.endsAfterEndOf(mSrcSingleEventWindow);
				} else {
					return !clonePeriod.startsBeforeStartOf(mSrcSingleEventWindow0)
							&& !clonePeriod.endsAfterEndOf(mSrcSingleEventWindow);
				}
			//}
		// Valid clone that we don't need to retain
		} else {
			return true;
		}
	}

	@Override
	public EventResult roundup(ClonerLog log) {
		log.log(log.createLogLine(ClonerLog.LOG_INFO, null,
				ClonerApp.translate(R.string.cloner_process_checking_for_deleted_events)), null);

		EventResult result = new EventResult();
		Set<Long> cloneIds = this.getRemainingCloneIds();

		int eventNr = 0;
		for (long cloneId : cloneIds) {
			eventNr++;
			Event clone = DbEvent.get(mEventsTable, cloneId);
			if (clone != null) {
				this.initLogLines(log, "" + eventNr);
				if (!clone.isDeleted()) {
					if (this.shouldDeleteClone(clone)) {
						// Check to see if we can delete within limits
						if (!Limits.canModify(Limits.TYPE_EVENT)) {
							this.log(ClonerLog.LOG_WARNING,
									ClonerApp.translate(R.string.cloner_log_event_limit_reached));
							result.completed = false;
							return result;
						}

						// The event must be deleted, because it represents
						// a clone of an already deleted event from this
						// calendar
						this.deleteEvent(clone);
						this.logSummary(ClonerLog.LOG_UPDATE, ClonerApp.translate(R.string.cloner_log_deleted), clone);
						result.updateCount++;
					} else {
						// The event is a valid clone
						this.logSummary(ClonerLog.LOG_INFO, ClonerApp.translate(R.string.cloner_log_skipped), clone,
								ClonerApp.translate(R.string.cloner_log_valid_clone));
					}
				} else {
					// The event is already being deleted
					this.logSummary(ClonerLog.LOG_INFO, ClonerApp.translate(R.string.cloner_log_skipped), clone,
							ClonerApp.translate(R.string.cloner_log_marked_deleted));
				}
			}
		}

		return result;
	}
}
