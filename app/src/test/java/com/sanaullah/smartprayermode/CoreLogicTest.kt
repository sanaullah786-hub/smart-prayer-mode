package com.sanaullah.smartprayermode

import com.sanaullah.smartprayermode.data.SettingsEntity
import com.sanaullah.smartprayermode.domain.HijriUtil
import com.sanaullah.smartprayermode.domain.Prayer
import com.sanaullah.smartprayermode.domain.PrayerCalculator
import com.sanaullah.smartprayermode.domain.QiblaUtil
import com.sanaullah.smartprayermode.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class CoreLogicTest {

    private val makkah = SettingsEntity(
        id = 1, locationSet = true, latitude = 21.4225, longitude = 39.8262,
        timezoneId = "Asia/Riyadh", calculationMethod = "UMM_AL_QURA", madhab = "SHAFI"
    )
    private val london = SettingsEntity(
        id = 1, locationSet = true, latitude = 51.5074, longitude = -0.1278,
        timezoneId = "Europe/London", calculationMethod = "MUSLIM_WORLD_LEAGUE"
    )

    @Test
    fun allSixTimesAreInOrder() {
        val day = PrayerCalculator.calculate(makkah, LocalDate.of(2026, 6, 21))
        val t = day.times.map { it.time }
        assertEquals(6, t.size)
        for (i in 0 until t.size - 1) assertTrue("times must increase", t[i].isBefore(t[i + 1]))
    }

    @Test
    fun fiveObligatoryPrayersExcludeSunrise() {
        val obligatory = Prayer.entries.filter { it.obligatory }
        assertEquals(5, obligatory.size)
        assertTrue(Prayer.SUNRISE !in obligatory)
    }

    @Test
    fun dhuhrInMakkahIsAroundTwentyPastTwelve() {
        val day = PrayerCalculator.calculate(makkah, LocalDate.of(2026, 6, 21))
        val local = day.of(Prayer.DHUHR).atZone(ZoneId.of("Asia/Riyadh"))
        val minutes = local.hour * 60 + local.minute
        assertTrue("Dhuhr was ${local.toLocalTime()}", minutes in (12 * 60 + 10)..(12 * 60 + 30))
    }

    @Test
    fun hanafiAsrIsLaterThanShafiAsr() {
        val date = LocalDate.of(2026, 11, 22)
        val shafi = PrayerCalculator.calculate(london.copy(madhab = "SHAFI"), date).of(Prayer.ASR)
        val hanafi = PrayerCalculator.calculate(london.copy(madhab = "HANAFI"), date).of(Prayer.ASR)
        assertTrue(hanafi.isAfter(shafi))
    }

    @Test
    fun manualAdjustmentShiftsTimeByExactMinutes() {
        val date = LocalDate.of(2026, 11, 22)
        val base = PrayerCalculator.calculate(makkah, date).of(Prayer.FAJR)
        val shifted = PrayerCalculator.calculate(makkah.copy(adjFajr = 5), date).of(Prayer.FAJR)
        assertEquals(Duration.ofMinutes(5), Duration.between(base, shifted))
    }

    @Test
    fun differentMethodsGiveDifferentFajr() {
        val date = LocalDate.of(2026, 11, 22)
        val mwl = PrayerCalculator.calculate(london.copy(calculationMethod = "MUSLIM_WORLD_LEAGUE"), date)
        val isna = PrayerCalculator.calculate(london.copy(calculationMethod = "NORTH_AMERICA"), date)
        assertTrue(mwl.of(Prayer.FAJR) != isna.of(Prayer.FAJR))
    }

    @Test
    fun nextPrayerAfterIshaIsTomorrowsFajr() {
        val zone = ZoneId.of("Asia/Riyadh")
        val date = LocalDate.of(2026, 11, 22)
        val isha = PrayerCalculator.calculate(makkah, date).of(Prayer.ISHA)
        val next = PrayerCalculator.nextObligatory(makkah, isha.plusSeconds(60))
        assertNotNull(next)
        assertEquals(Prayer.FAJR, next!!.prayer)
        assertEquals(date.plusDays(1), next.time.atZone(zone).toLocalDate())
    }

    @Test
    fun nextPrayerBeforeFajrIsFajrToday() {
        val date = LocalDate.of(2026, 11, 22)
        val fajr = PrayerCalculator.calculate(makkah, date).of(Prayer.FAJR)
        val next = PrayerCalculator.nextObligatory(makkah, fajr.minusSeconds(60))
        assertEquals("next=$next fajr=$fajr", Prayer.FAJR, next!!.prayer)
        assertEquals("next=$next fajr=$fajr", fajr, next.time)
    }

    @Test
    fun nextPrayerSkipsSunrise() {
        val date = LocalDate.of(2026, 11, 22)
        val day = PrayerCalculator.calculate(makkah, date)
        val next = PrayerCalculator.nextObligatory(makkah, day.of(Prayer.FAJR).plusSeconds(60))
        assertEquals(Prayer.DHUHR, next!!.prayer)
    }

    @Test
    fun clocksFollowDaylightSavingInLondon() {
        val zone = ZoneId.of("Europe/London")
        val before = PrayerCalculator.calculate(london, LocalDate.of(2026, 3, 28)).of(Prayer.DHUHR).atZone(zone)
        val after = PrayerCalculator.calculate(london, LocalDate.of(2026, 3, 30)).of(Prayer.DHUHR).atZone(zone)
        assertEquals(12, before.hour)
        assertEquals(13, after.hour)
    }

    @Test
    fun invalidMethodFallsBackInsteadOfCrashing() {
        val day = PrayerCalculator.calculate(makkah.copy(calculationMethod = "NOT_A_METHOD"), LocalDate.of(2026, 11, 22))
        assertEquals(6, day.times.size)
    }

    @Test
    fun hijriRoundTripAndRamadan() {
        val start = HijriUtil.firstOfMonth(1448, HijriUtil.RAMADAN)
        val parts = HijriUtil.toHijri(start)
        assertEquals(1448, parts.year)
        assertEquals(HijriUtil.RAMADAN, parts.month)
        assertEquals(1, parts.day)
        assertTrue(HijriUtil.isRamadan(start))
        assertTrue(!HijriUtil.isRamadan(start.minusDays(1)))
    }

    @Test
    fun ramadanCountdownIsPositiveAndBounded() {
        val today = LocalDate.of(2026, 11, 22)
        val days = HijriUtil.daysUntilRamadan(today)
        assertTrue("days=$days", days in 60..120)
        assertEquals(today.plusDays(days), HijriUtil.nextRamadanStart(today))
    }

    @Test
    fun qiblaBearingsAreSensible() {
        assertTrue("karachi=${QiblaUtil.bearing(24.8607, 67.0011)}", QiblaUtil.bearing(24.8607, 67.0011) in 255.0..275.0) // Karachi ~ west
        assertTrue("london=${QiblaUtil.bearing(51.5074, -0.1278)}", QiblaUtil.bearing(51.5074, -0.1278) in 110.0..125.0) // London ~ south-east
        val km = QiblaUtil.distanceKm(24.8607, 67.0011)
        assertTrue("km=$km", km in 2500.0..3200.0)
    }

    @Test
    fun countdownFormatting() {
        assertEquals("00:00:00", Fmt.hms(0))
        assertEquals("00:00:00", Fmt.hms(-5000))
        assertEquals("00:01:01", Fmt.hms(61_000))
        assertEquals("01:00:00", Fmt.hms(3_600_000))
        assertEquals("15:00", Fmt.ms(900_000))
        assertEquals("0:05", Fmt.mmss(5_000))
    }

    @Test
    fun defaultsMatchTheSpecification() {
        val d = SettingsEntity.defaults()
        assertTrue(d.autoPrayerMode)
        assertTrue(d.azanEnabled)
        assertEquals("MADINAH", d.azanSource)
        assertEquals(15, d.prayerDurationMinutes)
        assertEquals("SHAFI", d.madhab)
        assertTrue(!d.locationSet)
    }

    @Test
    fun notificationFlagsArePerPrayer() {
        val s = SettingsEntity.defaults().copy(notifDhuhr = false)
        assertTrue(s.notifFor(Prayer.FAJR))
        assertTrue(!s.notifFor(Prayer.DHUHR))
        assertTrue(!s.notifFor(Prayer.SUNRISE))
        assertEquals(10, s.reminderMinutes)
    }

    @Test
    fun userAzanFileIsPerSlot() {
        val s = SettingsEntity.defaults().copy(userUriMakkah = "content://x/makkah")
        assertEquals("content://x/makkah", s.azanUri("MAKKAH"))
        assertNull(s.azanUri("MADINAH"))
        assertNull(s.azanUri("CUSTOM"))
    }
}
