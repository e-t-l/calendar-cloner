package com.dizzl.android.CalendarCloner;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.provider.CalendarContract.Reminders;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

public class RuleActivity extends PreferenceActivity {
	private boolean mChanged = false;
	private boolean mNewRule = true;
	private Rule mRule = null;

	static final String[] mMethodNames = new String[] { ClonerApp.translate(R.string.rule_method_clone),
			ClonerApp.translate(R.string.rule_method_move), ClonerApp.translate(R.string.rule_method_aggregate) };
	static final String[] mMethodKeys = new String[] { "" + Rule.METHOD_CLONE, "" + Rule.METHOD_MOVE,
			"" + Rule.METHOD_AGGREGATE };
	static final String mOn = ClonerApp.translate(R.string.rule_clone_self_attendee_status_reverse_on_summary);
	static final String mOff = ClonerApp.translate(R.string.rule_clone_self_attendee_status_reverse_off_summary);
	static final String[] mSyncPeriodBeforeNames = new String[] {
			ClonerApp.translate(R.string.msg_n_days_back, new String[] { "0" }),
			ClonerApp.translate(R.string.msg_n_day_back, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_days_back, new String[] { "3" }),
			ClonerApp.translate(R.string.msg_n_week_back, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_weeks_back, new String[] { "2" }),
			ClonerApp.translate(R.string.msg_n_month_back, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_months_back, new String[] { "3" }),
			ClonerApp.translate(R.string.msg_n_year_back, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_years_back, new String[] { "2" }),
			ClonerApp.translate(R.string.msg_n_years_back, new String[] { "5" }), 
			ClonerApp.translate(R.string.msg_all) };
	static final String[] mSyncPeriodAfterNames = new String[] {
			ClonerApp.translate(R.string.msg_n_days_forward, new String[] { "0" }),
			ClonerApp.translate(R.string.msg_n_day_forward, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_days_forward, new String[] { "3" }),
			ClonerApp.translate(R.string.msg_n_week_forward, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_weeks_forward, new String[] { "2" }),
			ClonerApp.translate(R.string.msg_n_month_forward, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_months_forward, new String[] { "3" }),
			ClonerApp.translate(R.string.msg_n_year_forward, new String[] { "1" }),
			ClonerApp.translate(R.string.msg_n_years_forward, new String[] { "2" }),
			ClonerApp.translate(R.string.msg_n_years_forward, new String[] { "5" }),
			ClonerApp.translate(R.string.msg_all) };
	static final String[] mEventTypeFilterKeys = { "" + Rule.EVENT_TYPE_ALL, "" + Rule.EVENT_TYPE_SIMPLE,
			"" + Rule.EVENT_TYPE_RECURRING };
	static final String[] mEventTypeFilterNames = { ClonerApp.translate(R.string.rule_event_type_all),
			ClonerApp.translate(R.string.rule_event_type_simple),
			ClonerApp.translate(R.string.rule_event_type_recurring) };
	static final String[] mHashMethodKeys = { "" + Rule.HASH_METHOD_SOURCE_CALENDAR, "" + Rule.HASH_METHOD_RULE_ID,
			"" + Rule.HASH_METHOD_MANUAL };
	static final String[] mHashMethodNames = { ClonerApp.translate(R.string.rule_hash_method_source_calendar),
			ClonerApp.translate(R.string.rule_hash_method_rule_id),
			ClonerApp.translate(R.string.rule_hash_method_manual) };

	public static class RuleSettingsFragment extends PreferenceFragment {
		private RuleActivity mActivity = null;
		private Rule mRule = null;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.rule_preferences);
			mActivity = (RuleActivity) this.getActivity();
			mRule = mActivity.mRule;
			this.fillFields();
		}

		private final OnPreferenceChangeListener mChangeListener = new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				// Try to get a lock on the rule. Can only be done if it is not
				// processed in the background
				int lockSecret = mRule.tryLock();
				if (lockSecret == 0) {
					// Rule is busy so we can't edit
					ClonerApp.toast(ClonerApp.translate(R.string.msg_rule_busy));
					return false;
				}

				boolean updated = true;
				// We have the lock, so not edit the rule as the user indicated
				try {
					// Rule settings
					final String key = preference.getKey();
					if (key.contentEquals("ruleEnabled")) {
						mRule.setEnabled((Boolean) newValue);
					} else {
						if (ClonerApp.getSettings().isClonerEnabled() && mRule.isEnabled()) {
							// Disable the rule any time someone changes a
							// preference and the cloner is enabled
							mRule.setEnabled(false);
							ClonerApp.toast(ClonerApp.translate(R.string.msg_rule_disabled));
						}
					}
					if (key.contentEquals("ruleName")) {
						mRule.setName((String) newValue);
					}
					if (key.contentEquals("ruleMethod")) {
						try {
							int method = Integer.parseInt((String) newValue);
							mRule.setMethod(method);
							updated = mRule.getMethod() == method;
						} catch (Exception e) {
						}
					}
					if (key.contentEquals("ruleSrcCalendar")) {
						mRule.setSrcCalendarRef((String) newValue);
						// Reset destination if equal to source
						if (mRule.getDstCalendarRef().contentEquals((String) newValue)) {
							mRule.clearDstCalendarRef();
						}
					}
					if (key.contentEquals("ruleDstCalendar")) {
						mRule.setDstCalendarRef((String) newValue);
						// Reset source if equal to destination
						if (mRule.getSrcCalendarRef().contentEquals((String) newValue)) {
							mRule.clearSrcCalendarRef();
						}
					}

					// Event selection settings
					if (key.contentEquals("ruleSyncPeriodBefore")) {
						updated = mRule.setSyncPeriodBefore(Long.parseLong((String) newValue));
						if (!updated) {
							ClonerApp.toast(ClonerApp.translate(R.string.rule_sync_period_limited));
							updated = mRule.getSyncPeriodBefore() != Long.parseLong((String) newValue);
						}
					}
					if (key.contentEquals("ruleSyncPeriodAfter")) {
						updated = mRule.setSyncPeriodAfter(Long.parseLong((String) newValue));
						if (!updated) {
							ClonerApp.toast(ClonerApp.translate(R.string.rule_sync_period_limited));
							updated = mRule.getSyncPeriodAfter() != Long.parseLong((String) newValue);
						}
					}
					if (key.contentEquals("ruleIncludeClones")) {
						mRule.setIncludeClones((Boolean) newValue);
						updated = mRule.getIncludeClones() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleUseEventFilters")) {
						mRule.setUseEventFilters((Boolean) newValue);
						updated = mRule.useEventFilters() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleEventTypeFilter")) {
						try {
							mRule.setEventTypeFilter(Integer.parseInt((String) newValue));
						} catch (Exception e) {
						}
					}
					if (key.contentEquals("ruleTitleMustContain")) {
						mRule.setTitleMustContain((String) newValue);
					}
					if (key.contentEquals("ruleTitleMustNotContain")) {
						mRule.setTitleMustNotContain((String) newValue);
					}
					if (key.contentEquals("ruleLocationMustContain")) {
						mRule.setLocationMustContain((String) newValue);
					}
					if (key.contentEquals("ruleLocationMustNotContain")) {
						mRule.setLocationMustNotContain((String) newValue);
					}
					if (key.contentEquals("ruleDescriptionMustContain")) {
						mRule.setDescriptionMustContain((String) newValue);
					}
					if (key.contentEquals("ruleDescriptionMustNotContain")) {
						mRule.setDescriptionMustNotContain((String) newValue);
					}
					if (key.contentEquals("ruleAccessLevels")) {
						@SuppressWarnings("unchecked")
						Set<String> keys = (Set<String>) newValue;
						AccessLevels accessLevels = new AccessLevels(false);
						for (int index = 0; index < accessLevels.getCount(); index++) {
							accessLevels.selectByKey(accessLevels.getKey(index),
									keys.contains("" + accessLevels.getKey(index)));
						}
						mRule.setAccessLevels(accessLevels);
					}
					if (key.contentEquals("ruleAttendeeStatuses")) {
						@SuppressWarnings("unchecked")
						Set<String> keys = (Set<String>) newValue;
						AttendeeStatuses attendeeStatuses = new AttendeeStatuses(false);
						for (int index = 0; index < attendeeStatuses.getCount(); index++) {
							attendeeStatuses.selectByKey(attendeeStatuses.getKey(index),
									keys.contains("" + attendeeStatuses.getKey(index)));
						}
						mRule.setAttendeeStatuses(attendeeStatuses);
					}
					if (key.contentEquals("ruleAvailabilities")) {
						@SuppressWarnings("unchecked")
						Set<String> keys = (Set<String>) newValue;
						Availabilities availabilities = new Availabilities(false);
						for (int index = 0; index < availabilities.getCount(); index++) {
							availabilities.selectByKey(availabilities.getKey(index),
									keys.contains("" + availabilities.getKey(index)));
						}
						mRule.setAvailabilities(availabilities);
					}
					if (key.contentEquals("ruleEventStatuses")) {
						@SuppressWarnings("unchecked")
						Set<String> keys = (Set<String>) newValue;
						EventStatuses eventStatuses = new EventStatuses(false);
						for (int index = 0; index < eventStatuses.getCount(); index++) {
							eventStatuses.selectByKey(eventStatuses.getKey(index),
									keys.contains("" + eventStatuses.getKey(index)));
						}
						mRule.setEventStatuses(eventStatuses);
					}
					if (key.contentEquals("ruleWeekdays")) {
						@SuppressWarnings("unchecked")
						Set<String> keys = (Set<String>) newValue;
						Weekdays weekdays = new Weekdays(false);
						for (int index = 0; index < weekdays.getCount(); index++) {
							weekdays.selectByKey(weekdays.getKey(index), keys.contains("" + weekdays.getKey(index)));
						}
						mRule.setWeekdays(weekdays);
					}

					// Content settings
					if (key.contentEquals("ruleCloneTitle")) {
						mRule.setCloneTitle((Boolean) newValue);
						updated = mRule.getCloneTitle() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCustomTitle")) {
						mRule.setCustomTitle((String) newValue);
					}
					if (key.contentEquals("ruleCloneLocation")) {
						mRule.setCloneLocation((Boolean) newValue);
						updated = mRule.getCloneLocation() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCustomLocation")) {
						mRule.setCustomLocation((String) newValue);
					}
					if (key.contentEquals("ruleCloneDescription")) {
						mRule.setCloneDescription((Boolean) newValue);
						updated = mRule.getCloneDescription() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCustomDescription")) {
						mRule.setCustomDescription((String) newValue);
					}
					if (key.contentEquals("ruleCloneSelfAttendeeStatus")) {
						mRule.setCloneSelfAttendeeStatus((Boolean) newValue);
						updated = mRule.getCloneSelfAttendeeStatus() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCloneSelfAttendeeStatusReverse")) {
						mRule.setCloneSelfAttendeeStatusReverse((Boolean) newValue);
					}
					if (key.contentEquals("ruleSelfAttendeeName")) {
						mRule.setSelfAttendeeName((String) newValue);
					}
					if (key.contentEquals("ruleReserveBefore")) {
						try {
							int rb = Integer.parseInt((String) newValue);
							mRule.setReserveBefore(rb >= 0 ? rb : 0);
						} catch (Exception e) {
						}
					}
					if (key.contentEquals("ruleReserveAfter")) {
						try {
							int ra = Integer.parseInt((String) newValue);
							mRule.setReserveAfter(ra >= 0 ? ra : 0);
						} catch (Exception e) {
						}
					}
					if (key.contentEquals("ruleCustomAccessLevel")) {
						try {
							mRule.setCustomAccessLevel(Integer.parseInt((String) newValue));
						} catch (Exception e) {
						}
					}
					if (key.contentEquals("ruleCustomAvailability")) {
						try {
							mRule.setCustomAvailability(Integer.parseInt((String) newValue));
						} catch (Exception e) {
						}
					}

					// Attendee options
					if (key.contentEquals("ruleCloneAttendees")) {
						mRule.setCloneAttendees((Boolean) newValue);
						updated = mRule.getCloneAttendees() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleUseDummyEmailAddresses")) {
						mRule.setUseDummyEmailAddresses((Boolean) newValue);
					}
					if (key.contentEquals("ruleDummyEmailDomain")) {
						mRule.setDummyEmailDomain((String) newValue);
					}
					if (key.contentEquals("ruleAttendeesAsText")) {
						mRule.setAttendeesAsText((Boolean) newValue);
						updated = mRule.getAttendeesAsText() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCustomAttendee")) {
						mRule.setCustomAttendee((Boolean) newValue);
						updated = mRule.getCustomAttendee() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCustomAttendeeName")) {
						mRule.setCustomAttendeeName((String) newValue);
					}
					if (key.contentEquals("ruleCustomAttendeeEmail")) {
						mRule.setCustomAttendeeEmail((String) newValue);
					}

					// Reminder options
					if (key.contentEquals("ruleCloneReminders")) {
						mRule.setCloneReminders((Boolean) newValue);
						updated = mRule.getCloneReminders() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCustomReminder")) {
						mRule.setCustomReminder((Boolean) newValue);
						updated = mRule.getCustomReminder() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleCustomReminderMethod")) {
						mRule.setCustomReminderMethod(Integer.parseInt((String) newValue));
					}
					if (key.contentEquals("ruleCustomReminderMinutes")) {
						try {
							String val = (String) newValue;
							if (val.contentEquals("")) {
								mRule.setCustomReminderMinutes(Reminders.MINUTES_DEFAULT);
							} else {
								int crm = Integer.parseInt((String) newValue);
								mRule.setCustomReminderMinutes(crm >= 0 ? crm : 0);
							}
						} catch (Exception e) {
						}
					}

					// Additional options
					if (key.contentEquals("ruleRetainClonesOutsideSourceEventWindow")) {
						mRule.setRetainClonesOutsideSourceEventWindow((Boolean) newValue);
						updated = mRule.getRetainClonesOutsideSourceEventWindow() == (Boolean) newValue;
					}
					if (key.contentEquals("ruleHashMethod")) {
						try {
							mRule.setHash(Integer.parseInt((String) newValue), mRule.getHash());
						} catch (Exception e) {
						}
					}
					if (key.contentEquals("ruleHash")) {
						mRule.setHash(mRule.getHashMethod(), (String) newValue);
					}
					if (key.contentEquals("ruleReadOnly")) {
						mRule.setReadOnly((Boolean) newValue);
					}

					if (updated) {
						fillFields();
					}
				} finally {
					mRule.tryRelease(lockSecret);
					mActivity.mChanged = true;
				}
				return updated;
			}
		};

		private String getDisplayName(DbCalendar cal) {
			String result = cal.getDisplayName();
			if (result == null) {
				result = "<" + ClonerApp.translate(R.string.rule_calendar_with_no_name) + ">";
			}
			return result;
		}

		private void fillCalendarPreference(String ref, ListPreference calPref, String[] excludeRefs,
				boolean needWriteAccess) {
			DbCalendar selected = null;
			List<String> calendarRefs = CalendarLoader.getValidRefs();
			List<DbCalendar> cals = new LinkedList<DbCalendar>();
			for (int index = 0; index < calendarRefs.size(); index++) {
				CalendarLoader.CalendarInfo info = CalendarLoader.getCalendarByRef(calendarRefs.get(index));
				DbCalendar cal = info.getCalendar();
				if (cal != null) {
					boolean excluded = false;
					for (int i = 0; i < excludeRefs.length; i++) {
						if (cal.getRef().contentEquals(excludeRefs[i])) {
							excluded = true;
						}
					}
					if (!excluded && cal.canAccess(needWriteAccess)) {
						cals.add(cal);
						if (cal.getRef().contentEquals(ref)) {
							selected = cal;
						}
					}
				}
			}

			String[] keys;
			String[] values;
			int count = 0;
			if (selected != null) {
				keys = new String[cals.size()];
				values = new String[cals.size()];
			} else {
				keys = new String[cals.size() + 1];
				values = new String[cals.size() + 1];
				keys[0] = "<" + ClonerApp.translate(R.string.rule_select_calendar) + ">";
				values[0] = "";
				count++;
			}

			for (DbCalendar cal : cals) {
				keys[count] = this.getDisplayName(cal);
				values[count] = cal.getRef();
				count++;
			}

			calPref.setEntries(keys);
			calPref.setEntryValues(values);
			if (cals.size() > 0) {
				if (selected != null) {
					calPref.setEnabled(true);
					calPref.setValue(selected.getRef());
					calPref.setSummary(this.getDisplayName(selected));
				} else {
					calPref.setEnabled(true);
					calPref.setValue("");
					calPref.setSummary("<" + ClonerApp.translate(R.string.rule_select_calendar) + ">");
				}
			} else {
				calPref.setEnabled(false);
				calPref.setValue("");
				calPref.setSummary(ClonerApp.translate(R.string.rule_no_calendars_found));
			}
		}

		private void fillCheckBox(String name, boolean checked, String checkedSummary, String uncheckedSummary,
				boolean enabled) {
			CheckBoxPreference cbp = (CheckBoxPreference) findPreference(name);
			cbp.setChecked(checked);
			cbp.setSummary(checked ? checkedSummary : uncheckedSummary);
			cbp.setEnabled(enabled);
			cbp.setOnPreferenceChangeListener(mChangeListener);
		}

		private void fillRadioSelect(String name, String value, final String[] keys, final String[] names,
				boolean enabled) {
			final ListPreference lp = (ListPreference) findPreference(name);
			lp.setEntries(names);
			lp.setEntryValues(keys);
			lp.setValue(value);
			this.setSummary(lp, value, keys, names);
			lp.setEnabled(enabled);
			lp.setOnPreferenceChangeListener(mChangeListener);
		}

		private void fillMultiSelect(String name, SelectList list, boolean enabled) {
			MultiSelectListPreference mslp;
			mslp = (MultiSelectListPreference) findPreference(name);

			String[] names = new String[list.getCount()];
			String[] keys = new String[list.getCount()];
			for (int index = 0; index < list.getCount(); index++) {
				names[index] = list.getName(index);
				keys[index] = "" + list.getKey(index);
			}

			mslp.setEntries(names);
			mslp.setEntryValues(keys);
			Set<String> set = new HashSet<String>();
			String summary = "";
			for (int index = 0; index < keys.length; index++) {
				if (list.isKeySelected(Integer.parseInt(keys[index]))) {
					set.add("" + keys[index]);
					if (!summary.contentEquals("")) {
						summary += ", ";
					}
					summary += names[index];
				}
			}
			mslp.setValues(set);
			if (summary.contentEquals("")) {
				mslp.setSummary(ClonerApp.translate(R.string.rule_no_selection));
			} else {
				mslp.setSummary(summary);
			}
			mslp.setEnabled(enabled);
			mslp.setOnPreferenceChangeListener(mChangeListener);
		}

		private void setSummary(Preference pref, String value, String[] keys, String[] names) {
			String summary = null;
			for (int index = 0; index < keys.length; index++) {
				if (keys[index].contentEquals(value)) {
					summary = names[index];
				}
			}
			pref.setSummary(summary != null ? summary : "");
		}

		private String[] getCustomAccessLevelKeys() {
			int count = mRule.getMethod() != Rule.METHOD_AGGREGATE ? 1 : 0;
			count += 4;
			String[] result = new String[count];
			count = 0;
			if (mRule.getMethod() != Rule.METHOD_AGGREGATE) {
				result[count++] = "" + Rule.CUSTOM_ACCESS_LEVEL_SOURCE;
			}
			result[count++] = "" + Rule.CUSTOM_ACCESS_LEVEL_DEFAULT;
			result[count++] = "" + Rule.CUSTOM_ACCESS_LEVEL_PRIVATE;
			result[count++] = "" + Rule.CUSTOM_ACCESS_LEVEL_CONFIDENTIAL;
			result[count++] = "" + Rule.CUSTOM_ACCESS_LEVEL_PUBLIC;
			return result;
		}

		private String[] getCustomAccessLevelNames() {
			int count = mRule.getMethod() != Rule.METHOD_AGGREGATE ? 1 : 0;
			count += 4;
			String[] result = new String[count];
			count = 0;
			if (mRule.getMethod() != Rule.METHOD_AGGREGATE) {
				result[count++] = ClonerApp.translate(R.string.rule_custom_access_level_source);
			}
			result[count++] = ClonerApp.translate(R.string.access_level_default);
			result[count++] = ClonerApp.translate(R.string.access_level_private);
			result[count++] = ClonerApp.translate(R.string.access_level_confidential);
			result[count++] = ClonerApp.translate(R.string.access_level_public);
			return result;
		}

		private String[] getCustomAvailabilityKeys(boolean hasAvailabilitySamsung) {
			int count = mRule.getMethod() != Rule.METHOD_AGGREGATE ? 1 : 0;
			count += 2;
			count += hasAvailabilitySamsung ? 1 : 0;
			String[] result = new String[count];
			count = 0;
			if (mRule.getMethod() != Rule.METHOD_AGGREGATE) {
				result[count++] = "" + Rule.CUSTOM_AVAILABILITY_SOURCE;
			}
			result[count++] = "" + Rule.CUSTOM_AVAILABILITY_BUSY;
			result[count++] = "" + Rule.CUSTOM_AVAILABILITY_FREE;
			if (hasAvailabilitySamsung) {
				result[count++] = "" + Rule.CUSTOM_AVAILABILITY_OUT_OF_OFFICE;
			}
			return result;
		}

		private String[] getCustomAvailabilityNames(boolean hasAvailabilitySamsung) {
			int count = mRule.getMethod() != Rule.METHOD_AGGREGATE ? 1 : 0;
			count += 2;
			count += hasAvailabilitySamsung ? 1 : 0;
			String[] result = new String[count];
			count = 0;
			if (mRule.getMethod() != Rule.METHOD_AGGREGATE) {
				result[count++] = ClonerApp.translate(R.string.rule_custom_availability_source);
			}
			result[count++] = ClonerApp.translate(R.string.availability_busy);
			result[count++] = ClonerApp.translate(R.string.availability_free);
			if (hasAvailabilitySamsung) {
				result[count++] = ClonerApp.translate(R.string.availability_out_of_office);
			}
			return result;
		}

		private void fillFields() {
			// Set the rule enable switch
			SwitchPreference ruleEnabled = (SwitchPreference) findPreference("ruleEnabled");
			ruleEnabled.setChecked(mRule.isEnabled());
			ruleEnabled.setSummary("Hash: " + mRule.getHash().substring(0, 10) + "...");
			ruleEnabled.setOnPreferenceChangeListener(mChangeListener);

			// Set the rule name
			EditTextPreference ruleName = (EditTextPreference) findPreference("ruleName");
			ruleName.setText(mRule.getName());
			ruleName.setSummary(mRule.getName());
			ruleName.setOnPreferenceChangeListener(mChangeListener);

			// Set the method
			ListPreference ruleMethod = (ListPreference) findPreference("ruleMethod");
			ruleMethod.setEntries(mMethodNames);
			ruleMethod.setEntryValues(mMethodKeys);
			ruleMethod.setValue("" + mRule.getMethod());
			this.setSummary(ruleMethod, "" + mRule.getMethod(), mMethodKeys, mMethodNames);
			ruleMethod.setOnPreferenceChangeListener(mChangeListener);

			// Set the source calendar
			ListPreference ruleSrcCalendar = (ListPreference) findPreference("ruleSrcCalendar");
			this.fillCalendarPreference(mRule.getSrcCalendarRef(), ruleSrcCalendar,
					new String[] { mRule.getDstCalendarRef() }, mRule.getMethod() == Rule.METHOD_MOVE);
			ruleSrcCalendar.setOnPreferenceChangeListener(mChangeListener);

			// Set the destination calendar
			ListPreference ruleDstCalendar = (ListPreference) findPreference("ruleDstCalendar");
			this.fillCalendarPreference(mRule.getDstCalendarRef(), ruleDstCalendar,
					new String[] { mRule.getSrcCalendarRef() }, true);
			ruleDstCalendar.setOnPreferenceChangeListener(mChangeListener);

			// Load the destination calendar for multiple purposes below
			final CalendarLoader.CalendarInfo info = CalendarLoader.getCalendarByRef(mRule.getDstCalendarRef());

			// Set the time period before
			String syncPeriodKeys[] = new String[Rule.SYNC_PERIODS.length];
			for (int index = 0; index < Rule.SYNC_PERIODS.length; index++) {
				syncPeriodKeys[index] = "" + Rule.SYNC_PERIODS[index];
			}
			this.fillRadioSelect("ruleSyncPeriodBefore", "" + mRule.getSyncPeriodBefore(), syncPeriodKeys,
					mSyncPeriodBeforeNames, true);

			// Set the time period after
			this.fillRadioSelect("ruleSyncPeriodAfter", "" + mRule.getSyncPeriodAfter(), syncPeriodKeys,
					mSyncPeriodAfterNames, true);

			// Set include clones checkbox
			this.fillCheckBox("ruleIncludeClones", mRule.getIncludeClones(),
					ClonerApp.translate(R.string.rule_include_clones_on_summary),
					ClonerApp.translate(R.string.rule_include_clones_off_summary), true);

			// Set the use event filters checkbox
			this.fillCheckBox("ruleUseEventFilters", mRule.useEventFilters(),
					ClonerApp.translate(R.string.rule_use_event_filters_on_summary),
					ClonerApp.translate(R.string.rule_use_event_filters_off_summary), true);

			// Set event type filter
			this.fillRadioSelect("ruleEventTypeFilter", "" + mRule.getEventTypeFilter(), mEventTypeFilterKeys,
					mEventTypeFilterNames, mRule.useEventFilters());

			// Set title must contain filter
			EditTextPreference ruleTitleMustContain = (EditTextPreference) findPreference("ruleTitleMustContain");
			ruleTitleMustContain.setText(mRule.getTitleMustContain());
			ruleTitleMustContain.setSummary(mRule.getTitleMustContain());
			ruleTitleMustContain.setEnabled(mRule.useEventFilters());
			ruleTitleMustContain.setOnPreferenceChangeListener(mChangeListener);

			// Set title must not contain filter
			EditTextPreference ruleTitleMustNotContain = (EditTextPreference) findPreference("ruleTitleMustNotContain");
			ruleTitleMustNotContain.setText(mRule.getTitleMustNotContain());
			ruleTitleMustNotContain.setSummary(mRule.getTitleMustNotContain());
			ruleTitleMustNotContain.setEnabled(mRule.useEventFilters());
			ruleTitleMustNotContain.setOnPreferenceChangeListener(mChangeListener);

			// Set location must contain filter
			EditTextPreference ruleLocationMustContain = (EditTextPreference) findPreference("ruleLocationMustContain");
			ruleLocationMustContain.setText(mRule.getLocationMustContain());
			ruleLocationMustContain.setSummary(mRule.getLocationMustContain());
			ruleLocationMustContain.setEnabled(mRule.useEventFilters());
			ruleLocationMustContain.setOnPreferenceChangeListener(mChangeListener);

			// Set location must not contain filter
			EditTextPreference ruleLocationMustNotContain = (EditTextPreference) findPreference("ruleLocationMustNotContain");
			ruleLocationMustNotContain.setText(mRule.getLocationMustNotContain());
			ruleLocationMustNotContain.setSummary(mRule.getLocationMustNotContain());
			ruleLocationMustNotContain.setEnabled(mRule.useEventFilters());
			ruleLocationMustNotContain.setOnPreferenceChangeListener(mChangeListener);

			// Set description must contain filter
			EditTextPreference ruleDescriptionMustContain = (EditTextPreference) findPreference("ruleDescriptionMustContain");
			ruleDescriptionMustContain.setText(mRule.getDescriptionMustContain());
			ruleDescriptionMustContain.setSummary(mRule.getDescriptionMustContain());
			ruleDescriptionMustContain.setEnabled(mRule.useEventFilters());
			ruleDescriptionMustContain.setOnPreferenceChangeListener(mChangeListener);

			// Set description must not contain filter
			EditTextPreference ruleDescriptionMustNotContain = (EditTextPreference) findPreference("ruleDescriptionMustNotContain");
			ruleDescriptionMustNotContain.setText(mRule.getDescriptionMustNotContain());
			ruleDescriptionMustNotContain.setSummary(mRule.getDescriptionMustNotContain());
			ruleDescriptionMustNotContain.setEnabled(mRule.useEventFilters());
			ruleDescriptionMustNotContain.setOnPreferenceChangeListener(mChangeListener);

			// Set access level selection
			this.fillMultiSelect("ruleAccessLevels", mRule.getAccessLevels(), mRule.useEventFilters());

			// Set attendee status selection
			this.fillMultiSelect("ruleAttendeeStatuses", mRule.getAttendeeStatuses(), mRule.useEventFilters());

			// Set availability selection
			this.fillMultiSelect("ruleAvailabilities", mRule.getAvailabilities(), mRule.useEventFilters());

			// Set event status selection
			this.fillMultiSelect("ruleEventStatuses", mRule.getEventStatuses(), mRule.useEventFilters());

			// Set weekday selection
			this.fillMultiSelect("ruleWeekdays", mRule.getWeekdays(), mRule.useEventFilters());

			// Set the clone title checkbox
			this.fillCheckBox("ruleCloneTitle", mRule.getCloneTitle(),
					ClonerApp.translate(R.string.rule_clone_title_on_summary),
					ClonerApp.translate(R.string.rule_clone_title_off_summary),
					mRule.getMethod() != Rule.METHOD_AGGREGATE);

			// Set the custom title
			EditTextPreference ruleCustomTitle = (EditTextPreference) findPreference("ruleCustomTitle");
			ruleCustomTitle.setText(mRule.getCustomTitle());
			ruleCustomTitle.setSummary(mRule.getCustomTitle());
			ruleCustomTitle.setEnabled(!mRule.getCloneTitle());
			ruleCustomTitle.setOnPreferenceChangeListener(mChangeListener);

			// Set the clone location checkbox
			this.fillCheckBox("ruleCloneLocation", mRule.getCloneLocation(),
					ClonerApp.translate(R.string.rule_clone_location_on_summary),
					ClonerApp.translate(R.string.rule_clone_location_off_summary),
					mRule.getMethod() != Rule.METHOD_AGGREGATE);

			// Set the custom location
			EditTextPreference ruleCustomLocation = (EditTextPreference) findPreference("ruleCustomLocation");
			ruleCustomLocation.setText(mRule.getCustomLocation());
			ruleCustomLocation.setSummary(mRule.getCustomLocation());
			ruleCustomLocation.setEnabled(!mRule.getCloneLocation());
			ruleCustomLocation.setOnPreferenceChangeListener(mChangeListener);

			// Set the clone description checkbox
			this.fillCheckBox("ruleCloneDescription", mRule.getCloneDescription(),
					ClonerApp.translate(R.string.rule_clone_description_on_summary),
					ClonerApp.translate(R.string.rule_clone_description_off_summary),
					mRule.getMethod() != Rule.METHOD_AGGREGATE);

			// Set the custom description
			EditTextPreference ruleCustomDescription = (EditTextPreference) findPreference("ruleCustomDescription");
			ruleCustomDescription.setText(mRule.getCustomDescription());
			ruleCustomDescription.setSummary(mRule.getCustomDescription());
			ruleCustomDescription.setEnabled(!mRule.getCloneDescription());
			ruleCustomDescription.setOnPreferenceChangeListener(mChangeListener);

			// Set the clone status checkbox
			this.fillCheckBox("ruleCloneSelfAttendeeStatus", mRule.getCloneSelfAttendeeStatus(),
					ClonerApp.translate(R.string.rule_clone_self_attendee_status_on_summary),
					ClonerApp.translate(R.string.rule_clone_self_attendee_status_off_summary),
					mRule.getMethod() != Rule.METHOD_AGGREGATE);

			// Set the clone status reverse checkbox
			String on = mOn;
			String off = mOff;
			if (mRule.getMethod() == Rule.METHOD_MOVE) {
				on = off = ClonerApp.translate(R.string.rule_not_relevant_for_move_rules);
			}
			this.fillCheckBox("ruleCloneSelfAttendeeStatusReverse", mRule.getCloneSelfAttendeeStatusReverse(), on, off,
					mRule.getCloneSelfAttendeeStatus() && mRule.getMethod() != Rule.METHOD_MOVE);

			// Set the self attendee name
			EditTextPreference ruleSelfAttendeeName = (EditTextPreference) findPreference("ruleSelfAttendeeName");
			ruleSelfAttendeeName.setText(mRule.getSelfAttendeeName());
			if (!mRule.getSelfAttendeeName().contentEquals("")) {
				ruleSelfAttendeeName.setSummary(mRule.getSelfAttendeeName());
			} else {
				ruleSelfAttendeeName.setSummary(ClonerApp.translate(R.string.rule_self_attendee_name_info));
			}
			ruleSelfAttendeeName.setEnabled(mRule.getCloneSelfAttendeeStatus()
					&& mRule.getCloneSelfAttendeeStatusReverse());
			ruleSelfAttendeeName.setOnPreferenceChangeListener(mChangeListener);

			// Set the custom access level
			ListPreference ruleCustomAccessLevel = (ListPreference) findPreference("ruleCustomAccessLevel");
			String[] customAccessLevelKeys = this.getCustomAccessLevelKeys();
			String[] customAccessLevelNames = this.getCustomAccessLevelNames();
			ruleCustomAccessLevel.setEntries(customAccessLevelNames);
			ruleCustomAccessLevel.setEntryValues(customAccessLevelKeys);
			ruleCustomAccessLevel.setValue("" + mRule.getCustomAccessLevel());
			this.setSummary(ruleCustomAccessLevel, "" + mRule.getCustomAccessLevel(), customAccessLevelKeys,
					customAccessLevelNames);
			ruleCustomAccessLevel.setEnabled(true);
			ruleCustomAccessLevel.setOnPreferenceChangeListener(mChangeListener);

			// Set the custom availability, depending on the destination
			// calendar type
			ListPreference ruleCustomAvailability = (ListPreference) findPreference("ruleCustomAvailability");
			boolean hasAvailabilitySamsung = info.getCalendar() != null
					&& Device.Samsung.supportsAvailabilitySamsung(info.getCalendar().getAccountType());
			String[] customavailabilityKeys = this.getCustomAvailabilityKeys(hasAvailabilitySamsung);
			String[] customavailabilityNames = this.getCustomAvailabilityNames(hasAvailabilitySamsung);
			ruleCustomAvailability.setEntries(customavailabilityNames);
			ruleCustomAvailability.setEntryValues(customavailabilityKeys);
			ruleCustomAvailability.setValue("" + mRule.getCustomAvailability());
			this.setSummary(ruleCustomAvailability, "" + mRule.getCustomAvailability(), customavailabilityKeys,
					customavailabilityNames);
			ruleCustomAvailability.setEnabled(true);
			ruleCustomAvailability.setOnPreferenceChangeListener(mChangeListener);

			// Set the clone attendees checkbox
			this.fillCheckBox("ruleCloneAttendees", mRule.getCloneAttendees(),
					ClonerApp.translate(R.string.rule_clone_attendees_on_summary),
					ClonerApp.translate(R.string.rule_clone_attendees_off_summary),
					mRule.getMethod() != Rule.METHOD_AGGREGATE);

			// Set the prevent attendee invites checkbox
			this.fillCheckBox("ruleUseDummyEmailAddresses", mRule.getUseDummyEmailAddresses(),
					ClonerApp.translate(R.string.rule_use_dummy_attendee_addresses_on_summary),
					ClonerApp.translate(R.string.rule_use_dummy_attendee_addresses_off_summary),
					mRule.getCloneAttendees());

			// Set the dummy email domain
			ListPreference ruleDummyEmailDomain = (ListPreference) findPreference("ruleDummyEmailDomain");
			final String[] dummyEmailDomainKeys = new String[] { Rule.DEFAULT_DUMMY_DOMAIN, Rule.BLACKHOLE_DOMAIN };
			final String[] dummyEmailDomainNames = new String[] { Rule.DEFAULT_DUMMY_DOMAIN, Rule.BLACKHOLE_DOMAIN };
			ruleDummyEmailDomain.setEntries(dummyEmailDomainNames);
			ruleDummyEmailDomain.setEntryValues(dummyEmailDomainKeys);
			ruleDummyEmailDomain.setValue(mRule.getDummyEmailDomain());
			ruleDummyEmailDomain.setSummary(ClonerApp.translate(R.string.rule_dummy_email_domain_summary,
					new String[] { mRule.getDummyEmailDomain() }));
			ruleDummyEmailDomain.setEnabled(mRule.getUseDummyEmailAddresses());
			ruleDummyEmailDomain.setOnPreferenceChangeListener(mChangeListener);

			// Set the attendees as text checkbox
			this.fillCheckBox("ruleAttendeesAsText", mRule.getAttendeesAsText(),
					ClonerApp.translate(R.string.rule_attendees_as_text_on_summary),
					ClonerApp.translate(R.string.rule_attendees_as_text_off_summary),
					mRule.getMethod() != Rule.METHOD_AGGREGATE);

			// Set the custom attendee checkbox
			this.fillCheckBox("ruleCustomAttendee", mRule.getCustomAttendee(),
					ClonerApp.translate(R.string.rule_custom_attendee_on_summary),
					ClonerApp.translate(R.string.rule_custom_attendee_off_summary), true);

			// Set the custom attendee name
			EditTextPreference ruleCustomAttendeeName = (EditTextPreference) findPreference("ruleCustomAttendeeName");
			ruleCustomAttendeeName.setText(mRule.getCustomAttendeeName());
			ruleCustomAttendeeName.setSummary(mRule.getCustomAttendeeName());
			ruleCustomAttendeeName.setEnabled(mRule.getCustomAttendee());
			ruleCustomAttendeeName.setOnPreferenceChangeListener(mChangeListener);

			// Set the custom attendee email
			EditTextPreference ruleCustomAttendeeEmail = (EditTextPreference) findPreference("ruleCustomAttendeeEmail");
			ruleCustomAttendeeEmail.setText(mRule.getCustomAttendeeEmail());
			ruleCustomAttendeeEmail.setSummary(mRule.getCustomAttendeeEmail());
			ruleCustomAttendeeEmail.setEnabled(mRule.getCustomAttendee());
			ruleCustomAttendeeEmail.setOnPreferenceChangeListener(mChangeListener);

			// Set the clone reminders checkbox
			this.fillCheckBox("ruleCloneReminders", mRule.getCloneReminders(),
					ClonerApp.translate(R.string.rule_clone_reminders_on_summary),
					ClonerApp.translate(R.string.rule_clone_reminders_off_summary),
					mRule.getMethod() != Rule.METHOD_AGGREGATE);

			// Set the custom reminder checkbox
			this.fillCheckBox("ruleCustomReminder", mRule.getCustomReminder(),
					ClonerApp.translate(R.string.rule_custom_reminder_on_summary),
					ClonerApp.translate(R.string.rule_custom_reminder_off_summary), true);

			// Set custom reminder method
			String disabledMessage = "";
			ReminderMethods methods = new ReminderMethods(false);
			// Calendar already loaded above, so reuse here
			DbCalendar cal = info.getCalendar();
			if (cal != null) {
				methods = cal.getAllowedReminders();
				// Remove DEFAULT method from selection
				methods.selectByKey(Reminders.METHOD_DEFAULT, false);
				methods.sort();
				disabledMessage = ClonerApp.translate(R.string.rule_custom_reminder_method_no_methods);
			} else {
				disabledMessage = CalendarLoader.getErrorString(info.getError(),
						ClonerApp.translate(R.string.calendar_destination));
			}

			ListPreference ruleCustomReminderMethod = (ListPreference) findPreference("ruleCustomReminderMethod");
			if (methods.getSelectedCount() > 0) {
				String[] names = methods.getSelectedNames();
				int[] keys = methods.getSelectedKeys();
				String[] values = new String[names.length];
				int selectedIndex = -1;
				for (int index = 0; index < values.length; index++) {
					values[index] = "" + keys[index];
					if (mRule.getCustomReminderMethod() == keys[index]) {
						selectedIndex = index;
					}
				}
				ruleCustomReminderMethod.setEntries(names);
				ruleCustomReminderMethod.setEntryValues(values);
				ruleCustomReminderMethod.setValue("" + mRule.getCustomReminderMethod());
				ruleCustomReminderMethod.setEnabled(mRule.getCustomReminder());
				ruleCustomReminderMethod.setSummary(selectedIndex >= 0 ? names[selectedIndex] : "");
			} else {
				ruleCustomReminderMethod.setEntries(new String[] {});
				ruleCustomReminderMethod.setEntryValues(new String[] {});
				ruleCustomReminderMethod.setValue("");
				ruleCustomReminderMethod.setEnabled(false);
				ruleCustomReminderMethod.setSummary(disabledMessage);
			}
			ruleCustomReminderMethod.setOnPreferenceChangeListener(mChangeListener);

			// Set custom reminder minutes
			EditTextPreference ruleCustomReminderMinutes = (EditTextPreference) findPreference("ruleCustomReminderMinutes");
			if (mRule.getCustomReminderMinutes() == Reminders.MINUTES_DEFAULT) {
				ruleCustomReminderMinutes.setText("");
				ruleCustomReminderMinutes
						.setSummary(ClonerApp.translate(R.string.rule_custom_reminder_default_minutes));
			} else {
				ruleCustomReminderMinutes.setText("" + mRule.getCustomReminderMinutes());
				ruleCustomReminderMinutes.setSummary(ClonerApp.translate(R.string.msg_n_minutes, new String[] { ""
						+ mRule.getCustomReminderMinutes() }));
			}
			ruleCustomReminderMinutes.setEnabled(mRule.getCustomReminder());
			ruleCustomReminderMinutes.setOnPreferenceChangeListener(mChangeListener);

			// Set reserve before
			EditTextPreference ruleReserveBefore = (EditTextPreference) findPreference("ruleReserveBefore");
			ruleReserveBefore.setText("" + mRule.getReserveBefore());
			ruleReserveBefore.setSummary(ClonerApp.translate(R.string.msg_n_minutes,
					new String[] { "" + mRule.getReserveBefore() }));
			ruleReserveBefore.setOnPreferenceChangeListener(mChangeListener);

			// Set reserve after
			ruleReserveBefore = (EditTextPreference) findPreference("ruleReserveAfter");
			ruleReserveBefore.setText("" + mRule.getReserveAfter());
			ruleReserveBefore.setSummary(ClonerApp.translate(R.string.msg_n_minutes,
					new String[] { "" + mRule.getReserveAfter() }));
			ruleReserveBefore.setOnPreferenceChangeListener(mChangeListener);

			// Set retain clones outside source event window
			on = ClonerApp.translate(R.string.rule_retain_clones_outside_source_event_window_on_summary);
			off = ClonerApp.translate(R.string.rule_retain_clones_outside_source_event_window_off_summary);
			if (mRule.getMethod() == Rule.METHOD_MOVE) {
				on = off = ClonerApp.translate(R.string.rule_not_relevant_for_move_rules);
			}
			this.fillCheckBox("ruleRetainClonesOutsideSourceEventWindow",
					mRule.getRetainClonesOutsideSourceEventWindow(), on, off, mRule.getMethod() != Rule.METHOD_MOVE);

			// Set hash method
			this.fillRadioSelect("ruleHashMethod", "" + mRule.getHashMethod(), mHashMethodKeys, mHashMethodNames, true);

			// Set the hash value
			EditTextPreference ruleHash = (EditTextPreference) findPreference("ruleHash");
			ruleHash.setText(mRule.getHash());
			ruleHash.setSummary(mRule.getHash());
			ruleHash.setEnabled(mRule.getHashMethod() == Rule.HASH_METHOD_MANUAL);
			ruleHash.setOnPreferenceChangeListener(mChangeListener);

			// Set the test mode
			on = ClonerApp.translate(R.string.rule_readonly_on_summary);
			off = ClonerApp.translate(R.string.rule_readonly_off_summary);
			this.fillCheckBox("ruleReadOnly", mRule.isReadOnly(), on, off, true);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Find out which rule to edit
		Bundle params = this.getIntent().getExtras();
		String ruleId = params.getString("ruleid");

		if (ruleId != null && !ruleId.contentEquals("")) {
			// Get the rule
			Settings settings = ClonerApp.getSettings();
			mRule = settings.getRuleById(ruleId);
			if (mRule != null) {
				mNewRule = false;
			}
		}

		if (mRule == null) {
			mRule = Settings.createNewRule();
		}

		super.onCreate(savedInstanceState);
		this.overridePendingTransition(R.anim.animation_enter_right, R.anim.animation_leave_left);
		this.getActionBar().setDisplayHomeAsUpEnabled(true);
		this.getActionBar().setTitle(ClonerApp.translate(R.string.rule_activity_title));
		// Edit away
		getFragmentManager().beginTransaction().replace(android.R.id.content, new RuleSettingsFragment()).commit();
	}

	@Override
	public void onBackPressed() {
		this.finishActivity(true);
		super.onBackPressed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// We can only cancel an insert, updates to existing rules are permanent
		// because we directly edit the rule
		if (mNewRule) {
			MenuItem mi = menu.add(ClonerApp.translate(R.string.cancel));
			mi.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					finishActivity(false);
					return true;

				}
			});
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in action bar clicked; go back
			this.finishActivity(true);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void finishActivity(boolean save) {
		if (save && mChanged) {
			final Settings settings = ClonerApp.getSettings();
			int count = settings.getNumberOfRules();
			if (mNewRule) {
				count++;
			}
			Rule[] rules = new Rule[count];
			for (int index = 0; index < count; index++) {
				rules[index] = settings.getRule(index);
			}
			if (mNewRule) {
				// Append the new rule
				rules[count - 1] = mRule;
			}
			mRule.markDirty();
			settings.setRules(rules, false);
		}
		Intent returnIntent = new Intent();
		returnIntent.putExtra("changed", save && mChanged);
		setResult(RESULT_OK, returnIntent);
		finish();
	}
}
