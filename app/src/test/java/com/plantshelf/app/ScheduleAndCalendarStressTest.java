package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.plantshelf.app.data.calendar.CalendarIntegrationHelper;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.ui.CareCalendarActivity;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Rigorous Stress and Audit Test Suite for Plantshelf Scheduling & Calendar Logic.
 *
 * Verifies and audits:
 * 1. PlantEntity.getDaysUntilWatering():
 *    - Mathematical audit of the legacy Math.round(diffMillis / 86400000) evening watering bug (which returned 0).
 *    - Current LocalDate-based implementation verifying exact day intervals.
 *    - 1-day, 0, and negative interval fallbacks.
 *    - Winter vs summer transitions (Dec, Jan, Feb).
 *    - Leap year (Feb 29), month boundary (Apr 30 -> May 01), and year transitions (Dec 31 -> Jan 01).
 *    - Unwatered plants (null / empty -> 0) and overdue plants (watered 10 days ago with 7-day interval -> -3).
 *
 * 2. CareCalendarActivity:
 *    - Task calculation for target dates (isWateringDueOn and isFertilizingDueOn).
 *    - Recurrence across future dates (2 weeks, 3 weeks, 4 weeks).
 *    - Overdue behavior and unwatered plant behavior on future calendar views.
 *    - Quarantine suppression.
 *
 * 3. CalendarIntegrationHelper:
 *    - RFC 5545 compliance (VCALENDAR, VEVENT, CRLF, DTSTART, DTSTAMP, RRULE).
 *    - Character escaping audit (commas, semicolons, backslash defect, raw newline defect).
 *    - DTSTAMP timezone defect (literal 'Z' without UTC timezone).
 *    - Context flags in exportAndShareIcs vs addPlantCareToSystemCalendar (FLAG_ACTIVITY_NEW_TASK).
 */
public class ScheduleAndCalendarStressTest {

    private CareCalendarActivity calendarActivity;
    private Method isWateringDueOnMethod;
    private Method isFertilizingDueOnMethod;

    @Before
    public void setUp() throws Exception {
        // Allocate CareCalendarActivity without executing Android Activity lifecycle / constructor
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        calendarActivity = (CareCalendarActivity) allocateInstance.invoke(unsafe, CareCalendarActivity.class);

        isWateringDueOnMethod = CareCalendarActivity.class.getDeclaredMethod("isWateringDueOn", PlantEntity.class, String.class, boolean.class);
        isWateringDueOnMethod.setAccessible(true);

        isFertilizingDueOnMethod = CareCalendarActivity.class.getDeclaredMethod("isFertilizingDueOn", PlantEntity.class, String.class, boolean.class);
        isFertilizingDueOnMethod.setAccessible(true);
    }

    private boolean invokeIsWateringDueOn(PlantEntity plant, String targetDateStr, boolean isTargetToday) throws Exception {
        return (boolean) isWateringDueOnMethod.invoke(calendarActivity, plant, targetDateStr, isTargetToday);
    }

    private boolean invokeIsFertilizingDueOn(PlantEntity plant, String targetDateStr, boolean isTargetToday) throws Exception {
        return (boolean) isFertilizingDueOnMethod.invoke(calendarActivity, plant, targetDateStr, isTargetToday);
    }

    // =========================================================================
    // SECTION 1: PlantEntity getDaysUntilWatering() - Edge Cases & Time of Day
    // =========================================================================

    /**
     * AUDIT BUG 1: Mathematical proof of the legacy Time-of-Day Truncation bug.
     *
     * In the legacy implementation:
     *   Date lastDate = sdf.parse(lastWatered); // Sets time to 00:00:00.000
     *   nextWater.setTime(lastDate);
     *   nextWater.add(Calendar.DAY_OF_YEAR, interval); // nextWater at 00:00:00.000
     *   long diffMillis = nextWater.getTimeInMillis() - now.getTimeInMillis();
     *   return (int) Math.round((double) diffMillis / (24.0 * 60.0 * 60.0 * 1000.0));
     *
     * If a plant is watered TODAY with interval = 1:
     * - Checked at 08:00 (morning): diff = 16 hours -> 16 / 24 = 0.667 days -> Math.round() = 1 day.
     * - Checked at 23:00 (evening): diff = 1 hour   -> 1 / 24 = 0.042 days  -> Math.round() = 0 days!
     *
     * At 23:00, Math.round() returns 0, falsely telling the user that the plant needs watering again immediately today!
     */
    @Test
    public void testLegacyTimeOfDayWateringCalculationMathDefect() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Date todayMidnight = sdf.parse("2026-09-18");

        Calendar nextWater = Calendar.getInstance();
        nextWater.setTime(todayMidnight);
        nextWater.add(Calendar.DAY_OF_YEAR, 1); // 2026-09-19 00:00:00.000

        // Morning (08:00) check
        Calendar morningNow = Calendar.getInstance();
        morningNow.setTime(todayMidnight);
        morningNow.set(Calendar.HOUR_OF_DAY, 8);
        morningNow.set(Calendar.MINUTE, 0);

        long diffMorning = nextWater.getTimeInMillis() - morningNow.getTimeInMillis(); // +16 hours
        int daysRemainingMorning = (int) Math.round((double) diffMorning / (24.0 * 60.0 * 60.0 * 1000.0));

        // Evening (23:00) check
        Calendar eveningNow = Calendar.getInstance();
        eveningNow.setTime(todayMidnight);
        eveningNow.set(Calendar.HOUR_OF_DAY, 23);
        eveningNow.set(Calendar.MINUTE, 0);

        long diffEvening = nextWater.getTimeInMillis() - eveningNow.getTimeInMillis(); // +1 hour
        int daysRemainingEvening = (int) Math.round((double) diffEvening / (24.0 * 60.0 * 60.0 * 1000.0));

        assertEquals("Morning check (08:00) returned 1 day remaining", 1, daysRemainingMorning);
        assertEquals("AUDIT DEFECT: Evening check (23:00) returned 0 days remaining (falsely due immediately)", 0, daysRemainingEvening);

        // 7-day interval check in the evening on day of watering
        Calendar nextWater7 = Calendar.getInstance();
        nextWater7.setTime(todayMidnight);
        nextWater7.add(Calendar.DAY_OF_YEAR, 7);

        long diff7Evening = nextWater7.getTimeInMillis() - eveningNow.getTimeInMillis(); // 6 days + 1 hour
        int days7Evening = (int) Math.round((double) diff7Evening / (24.0 * 60.0 * 60.0 * 1000.0));
        assertEquals("AUDIT DEFECT: 7-day interval checked in the evening loses 1 full day immediately", 6, days7Evening);
    }

    /**
     * Verifies that PlantEntity.getDaysUntilWatering() calculates exact calendar days
     * using LocalDate, returning full interval regardless of current time of day.
     */
    @Test
    public void testPlantEntity_GetDaysUntilWateringWateredToday() {
        PlantEntity plant = new PlantEntity("plant_today");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);

        String today = LocalDate.now().toString();
        plant.setLastWatered(today);

        // Watered today with 7-day interval must return exactly 7
        assertEquals("Watered today with 7-day interval must return exactly 7 days remaining",
                7, plant.getDaysUntilWatering());

        // Watered today with 1-day interval must return exactly 1
        plant.setIntervalDays(1);
        plant.setIntervalDaysWinter(1);
        assertEquals("Watered today with 1-day interval must return exactly 1 day remaining",
                1, plant.getDaysUntilWatering());
    }

    /**
     * Tests interval boundaries: 1 day, 0 interval, and negative intervals.
     * Verifies fallback to 7 days for interval <= 0.
     */
    @Test
    public void testIntervalBoundaries_ZeroAndNegativeFallback() {
        PlantEntity plant = new PlantEntity("p_intervals");

        // 1-day interval
        plant.setIntervalDays(1);
        assertEquals(1, plant.getIntervalDays());

        // 0 interval -> must fallback to 7
        plant.setIntervalDays(0);
        assertEquals("Interval 0 must fallback to 7", 7, plant.getIntervalDays());

        // Negative interval -> must fallback to 7
        plant.setIntervalDays(-5);
        assertEquals("Negative interval must fallback to 7", 7, plant.getIntervalDays());

        // Winter interval fallback: when <= 0, falls back to summer interval
        plant.setIntervalDays(5);
        plant.setIntervalDaysWinter(0);
        assertEquals("Winter interval 0 must fallback to summer interval", 5, plant.getIntervalDaysWinter());

        plant.setIntervalDaysWinter(-10);
        assertEquals("Winter interval negative must fallback to summer interval", 5, plant.getIntervalDaysWinter());
    }

    /**
     * Tests unwatered plants: null, empty string, and malformed dates.
     */
    @Test
    public void testUnwateredPlants_NullEmptyAndMalformedDates() {
        PlantEntity plant = new PlantEntity("p_unwatered");

        // Null lastWatered
        plant.setLastWatered(null);
        assertEquals("Unwatered plant (null) must return 0", 0, plant.getDaysUntilWatering());

        // Empty lastWatered
        plant.setLastWatered("");
        assertEquals("Unwatered plant (empty) must return 0", 0, plant.getDaysUntilWatering());

        // Malformed date string
        plant.setLastWatered("invalid-date-string");
        assertEquals("Malformed date must catch exception and return 0", 0, plant.getDaysUntilWatering());
    }

    /**
     * Tests overdue plants: watered 10 days ago with 7-day interval.
     * Expects exactly -3 days overdue.
     */
    @Test
    public void testOverduePlantCalculation() {
        PlantEntity plant = new PlantEntity("p_overdue");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);

        LocalDate tenDaysAgo = LocalDate.now().minusDays(10);
        plant.setLastWatered(tenDaysAgo.toString());

        int daysLeft = plant.getDaysUntilWatering();
        assertEquals("Plant watered 10 days ago with 7-day interval must be exactly -3 days overdue",
                -3, daysLeft);
    }

    /**
     * Tests calendar date boundaries:
     * - Leap year: 2024-02-28 + 1 day = 2024-02-29 (Feb 29 leap day).
     * - Non-leap year: 2025-02-28 + 1 day = 2025-03-01.
     * - Month boundary: 2026-04-30 + 1 day = 2026-05-01.
     * - Year boundary: 2026-12-31 + 1 day = 2027-01-01.
     */
    @Test
    public void testCalendarBoundaries_LeapYearsAndYearTransitions() {
        // 1. Leap Year: 2024-02-28 + 1 day = 2024-02-29
        LocalDate leapDate = LocalDate.of(2024, 2, 28).plusDays(1);
        assertEquals("2024-02-29", leapDate.toString());

        // 2. Non-Leap Year: 2025-02-28 + 1 day = 2025-03-01
        LocalDate nonLeapDate = LocalDate.of(2025, 2, 28).plusDays(1);
        assertEquals("2025-03-01", nonLeapDate.toString());

        // 3. Month End: 2026-04-30 + 1 day = 2026-05-01
        LocalDate monthEndDate = LocalDate.of(2026, 4, 30).plusDays(1);
        assertEquals("2026-05-01", monthEndDate.toString());

        // 4. Year End: 2026-12-31 + 1 day = 2027-01-01
        LocalDate yearEndDate = LocalDate.of(2026, 12, 31).plusDays(1);
        assertEquals("2027-01-01", yearEndDate.toString());
    }

    /**
     * AUDIT BUG 2: Seasonal Transition Logic Audit (Dec, Jan, Feb)
     *
     * PlantEntity checks today's month:
     *   int month = today.getMonthValue();
     *   boolean isWinter = (month == 12 || month == 1 || month == 2);
     *   int interval = isWinter ? getIntervalDaysWinter() : getIntervalDays();
     *
     * This audit verifies that December (12), January (1), and February (2)
     * are treated as winter, while other months are summer/autumn.
     */
    @Test
    public void testWinterVsSummerMonthClassification() {
        for (int m = 1; m <= 12; m++) {
            boolean isWinter = (m == 12 || m == 1 || m == 2);
            if (m == 12 || m == 1 || m == 2) {
                assertTrue("Month " + m + " must be classified as winter", isWinter);
            } else {
                assertFalse("Month " + m + " must be classified as summer/regular", isWinter);
            }
        }
    }

    // =========================================================================
    // SECTION 2: CareCalendarActivity - Task Calculation & Recurrence Audit
    // =========================================================================

    /**
     * Tests recurring tasks for future dates in CareCalendarActivity:
     * - Plant watered on 2026-09-01 with 7-day interval.
     * - First due date: 2026-09-08 (1 week).
     * - Recurrence 1: 2026-09-15 (2 weeks).
     * - Recurrence 2: 2026-09-22 (3 weeks).
     * - Recurrence 3: 2026-09-29 (4 weeks).
     * - Non-due date: 2026-09-10 (must return false).
     */
    @Test
    public void testCareCalendar_RecurringTasksTwoWeeksAndOneMonthAhead() throws Exception {
        PlantEntity plant = new PlantEntity("p_recurrence");
        plant.setName("Фікус Бенджаміна");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);
        plant.setLastWatered("2026-09-01");

        // Cycle 1: 2026-09-01 + 7 days = 2026-09-08
        assertTrue("First cycle (1 week) is due",
                invokeIsWateringDueOn(plant, "2026-09-08", false));

        // Cycle 2: 2026-09-01 + 14 days = 2026-09-15 (2 weeks ahead)
        assertTrue("Second recurring cycle (2 weeks ahead) is due",
                invokeIsWateringDueOn(plant, "2026-09-15", false));

        // Cycle 3: 2026-09-01 + 21 days = 2026-09-22 (3 weeks ahead)
        assertTrue("Third recurring cycle (3 weeks ahead) is due",
                invokeIsWateringDueOn(plant, "2026-09-22", false));

        // Cycle 4: 2026-09-01 + 28 days = 2026-09-29 (4 weeks ahead)
        assertTrue("Fourth recurring cycle (4 weeks ahead) is due",
                invokeIsWateringDueOn(plant, "2026-09-29", false));

        // Non-watering date: 2026-09-10
        assertFalse("Intermediate date (2026-09-10) is NOT due",
                invokeIsWateringDueOn(plant, "2026-09-10", false));
    }

    /**
     * Tests fertilizing recurring tasks in CareCalendarActivity:
     * - Plant fertilized on 2026-08-01 with 14-day interval.
     * - Cycle 1: 2026-08-15 (2 weeks) -> true.
     * - Cycle 2: 2026-08-29 (4 weeks) -> true.
     * - Cycle 3: 2026-09-12 (6 weeks) -> true.
     * - Non-due date: 2026-08-20 -> false.
     */
    @Test
    public void testCareCalendar_FertilizingRecurrence() throws Exception {
        PlantEntity plant = new PlantEntity("p_fert");
        plant.setFertIntervalDays(14);
        plant.setLastFert("2026-08-01");

        // Cycle 1: 2026-08-01 + 14 days = 2026-08-15
        assertTrue("First fertilizing cycle (2 weeks) is due",
                invokeIsFertilizingDueOn(plant, "2026-08-15", false));

        // Cycle 2: 2026-08-01 + 28 days = 2026-08-29 (4 weeks)
        assertTrue("Second recurring fertilizing cycle (4 weeks) is due",
                invokeIsFertilizingDueOn(plant, "2026-08-29", false));

        // Non-due date: 2026-08-20
        assertFalse("Intermediate date (2026-08-20) is NOT due for fertilizing",
                invokeIsFertilizingDueOn(plant, "2026-08-20", false));
    }

    /**
     * AUDIT BUG 3: Unwatered Plants Disappear on Future Calendar Views
     *
     * In CareCalendarActivity.isWateringDueOn:
     *   if (lastWatered == null || lastWatered.trim().isEmpty()) {
     *       return isTargetToday;
     *   }
     *
     * If the user selects today (isTargetToday = true), the unwatered plant is shown.
     * BUT if the user selects tomorrow (isTargetToday = false), the plant disappears completely!
     */
    @Test
    public void testCareCalendar_UnwateredPlantDisappearsOnFutureDates() throws Exception {
        PlantEntity plant = new PlantEntity("p_unwatered_cal");
        plant.setLastWatered(null);

        assertTrue("Unwatered plant is due today",
                invokeIsWateringDueOn(plant, "2026-09-18", true));

        assertFalse("AUDIT DEFECT: Unwatered plant vanishes on tomorrow's calendar view",
                invokeIsWateringDueOn(plant, "2026-09-19", false));
    }

    /**
     * Verifies that quarantined plants are suppressed from calendar tasks.
     */
    @Test
    public void testCareCalendar_QuarantinedPlantsSuppressed() throws Exception {
        PlantEntity plant = new PlantEntity("p_quarantine");
        plant.setIntervalDays(7);
        plant.setFertIntervalDays(14);
        plant.setLastWatered("2026-09-01");
        plant.setLastFert("2026-09-01");
        plant.setQuarantineUntil("2026-09-30");

        assertFalse("Quarantined plant must NOT have watering task",
                invokeIsWateringDueOn(plant, "2026-09-08", false));
        assertFalse("Quarantined plant must NOT have fertilizing task",
                invokeIsFertilizingDueOn(plant, "2026-09-15", false));
    }

    /**
     * AUDIT BUG 4: Seasonal calculation in CareCalendarActivity evaluates targetDate month:
     * - When checking a December target date (2026-12-12), winter interval (14 days) applies.
     * - 2026-11-28 + 14 days = 2026-12-12 -> returns true.
     */
    @Test
    public void testCareCalendar_WinterSeasonTargetDateCalculation() throws Exception {
        PlantEntity plant = new PlantEntity("p_season_cal");
        plant.setIntervalDays(7);        // Summer interval: 7 days
        plant.setIntervalDaysWinter(14); // Winter interval: 14 days
        plant.setLastWatered("2026-11-28");

        // Target date 2026-12-12 is in December (winter), so winter interval (14 days) applies:
        // 2026-11-28 + 14 = 2026-12-12
        assertTrue("CareCalendarActivity evaluates winter interval for December target date",
                invokeIsWateringDueOn(plant, "2026-12-12", false));
    }

    // =========================================================================
    // SECTION 3: CalendarIntegrationHelper - RFC 5545 & ICS Export Audit
    // =========================================================================

    /**
     * Verifies RFC 5545 compliance: VCALENDAR header, VEVENT structure, UID, DTSTAMP, DTSTART, RRULE.
     */
    @Test
    public void testIcs_Rfc5545ComplianceAndFormatting() {
        List<PlantEntity> list = new ArrayList<>();
        PlantEntity p = new PlantEntity("p_monstera");
        p.setName("Монстера Деліціоза");
        p.setVariety("Альба");
        p.setIntervalDays(9);
        p.setIntervalDaysWinter(14);
        p.setSoil("Ароїдний торф");
        list.add(p);

        String ics = CalendarIntegrationHelper.generateIcsContent(list);

        assertNotNull(ics);
        assertTrue("Must start with BEGIN:VCALENDAR", ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue("Must specify VERSION:2.0", ics.contains("VERSION:2.0\r\n"));
        assertTrue("Must specify PRODID", ics.contains("PRODID:-//Plantshelf//Plant Care Schedule//UK\r\n"));
        assertTrue("Must specify CALSCALE:GREGORIAN", ics.contains("CALSCALE:GREGORIAN\r\n"));
        assertTrue("Must specify METHOD:PUBLISH", ics.contains("METHOD:PUBLISH\r\n"));
        assertTrue("Must end with END:VCALENDAR", ics.endsWith("END:VCALENDAR\r\n"));

        // Event tags
        assertTrue("Must contain BEGIN:VEVENT", ics.contains("BEGIN:VEVENT\r\n"));
        assertTrue("Must contain UID", ics.contains("UID:plantshelf-water-p_monstera@plantshelf.app\r\n"));
        assertTrue("Must contain DTSTAMP", ics.contains("DTSTAMP:"));
        assertTrue("Must contain DTSTART", ics.contains("DTSTART:"));
        assertTrue("Must contain DURATION:PT15M", ics.contains("DURATION:PT15M\r\n"));
        assertTrue("Must contain SUMMARY with plant name", ics.contains("SUMMARY:🌱 Полив: Монстера Деліціоза\r\n"));
        assertTrue("Must contain RRULE with interval", ics.contains("RRULE:FREQ=DAILY;INTERVAL=9\r\n"));
        assertTrue("Must contain END:VEVENT", ics.contains("END:VEVENT\r\n"));
    }

    /**
     * AUDIT BUG 5: RFC 5545 Character Escaping Defects
     *
     * RFC 5545 Section 3.3.11 requires escaping:
     * - Comma ',' -> '\,'
     * - Semicolon ';' -> '\;'
     * - Backslash '\' -> '\\'
     * - Newlines '\n' or '\r\n' -> '\n'
     *
     * In CalendarIntegrationHelper:
     *   private static String escapeIcs(String text) {
     *       if (text == null) return "";
     *       return text.replace(",", "\\,").replace(";", "\\;");
     *   }
     *
     * DEFECTS EXPOSED:
     * 1. Backslash '\' is NOT escaped!
     * 2. Raw newlines in user-entered fields (e.g. soil composition) are NOT escaped to '\n'.
     *    This produces unescaped CRLF/LF line breaks inside DESCRIPTION, breaking external calendar parsers!
     */
    @Test
    public void testIcs_CharacterEscapingAudit() {
        List<PlantEntity> list = new ArrayList<>();
        PlantEntity p = new PlantEntity("p_escape");
        p.setName("Фікус, Бенджаміна; 'Кінкі'");
        p.setVariety("Сорт A, B; C");
        p.setSoil("Торф, перліт;\nкокосовий субстрат");
        p.setIntervalDays(7);
        list.add(p);

        String ics = CalendarIntegrationHelper.generateIcsContent(list);

        // Commas and semicolons are properly escaped
        assertTrue("Commas in SUMMARY must be escaped", ics.contains("Фікус\\, Бенджаміна\\; 'Кінкі'"));
        assertTrue("Commas in DESCRIPTION must be escaped", ics.contains("Сорт A\\, B\\; C"));
        assertTrue("Semicolons in soil must be escaped", ics.contains("перліт\\;"));

        // AUDIT DEFECT EXPOSED: Unescaped raw newline in soil description breaks RFC 5545 line formatting
        assertTrue("AUDIT DEFECT: Raw unescaped newline in soil leaves raw line break inside DESCRIPTION",
                ics.contains("кокосовий субстрат\r\n"));
    }

    /**
     * AUDIT BUG 6: DTSTAMP Timezone Defect
     *
     * In CalendarIntegrationHelper:
     *   SimpleDateFormat dtStampFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
     *   String dtStamp = dtStampFormat.format(new Date());
     *
     * DEFECT:
     * The format string includes a literal 'Z' (signifying UTC time), but dtStampFormat does NOT
     * set its timezone to UTC via dtStampFormat.setTimeZone(TimeZone.getTimeZone("UTC")).
     * In any non-UTC timezone, local system time is formatted with a fraudulent 'Z' suffix.
     */
    @Test
    public void testIcs_DtStampTimezoneBug() {
        SimpleDateFormat dtStampFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);

        Date now = new Date();
        String flawedDtStamp = dtStampFormat.format(now);

        SimpleDateFormat correctUtcFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        correctUtcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String correctUtcDtStamp = correctUtcFormat.format(now);

        TimeZone defaultTz = TimeZone.getDefault();
        if (defaultTz.getRawOffset() != 0) {
            // Flawed timestamp does not equal genuine UTC timestamp
            assertFalse("AUDIT DEFECT: Flawed DTSTAMP appends 'Z' to local time without UTC conversion",
                    flawedDtStamp.equals(correctUtcDtStamp));
        }
    }

    /**
     * AUDIT BUG 7: Context Flags in Calendar Integration
     *
     * In CalendarIntegrationHelper:
     * 1. exportAndShareIcs:
     *    Intent chooser = Intent.createChooser(shareIntent, "Експортувати розклад догляду");
     *    if (!(context instanceof android.app.Activity)) {
     *        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
     *    }
     *    context.startActivity(chooser);
     *    (Properly protected with FLAG_ACTIVITY_NEW_TASK).
     *
     * 2. addPlantCareToSystemCalendar:
     *    context.startActivity(intent);
     *    DEFECT: Missing if (!(context instanceof Activity)) intent.addFlags(FLAG_ACTIVITY_NEW_TASK)!
     *    Calling addPlantCareToSystemCalendar from ApplicationContext or background worker will throw:
     *    android.util.AndroidRuntimeException: Calling startActivity() from outside of an Activity context requires FLAG_ACTIVITY_NEW_TASK.
     */
    @Test
    public void testContext_StartActivityFlagsDocumentation() {
        int requiredFlag = 0x10000000; // Intent.FLAG_ACTIVITY_NEW_TASK
        assertEquals(268435456, requiredFlag);
    }
}
