package com.sanaullah.smartprayermode.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanaullah.smartprayermode.domain.Prayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-row settings table. [id] has no default on purpose so Kotlin does not
 * generate a no-arg constructor that Room could pick instead of the full one.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int,
    val onboardingDone: Boolean = false,

    // Behaviour
    val autoPrayerMode: Boolean = true,
    val autoLocation: Boolean = true,
    val dndDuringPrayer: Boolean = false,
    val vibration: Boolean = true,
    val prayerDurationMinutes: Int = 15,

    // Azan
    val azanEnabled: Boolean = true,
    val azanSource: String = "MADINAH",
    val customAzanUri: String? = null,
    val azanVolume: Float = 0.8f,

    // Calculation
    val calculationMethod: String = "MUSLIM_WORLD_LEAGUE",
    val madhab: String = "SHAFI",
    val adjFajr: Int = 0,
    val adjSunrise: Int = 0,
    val adjDhuhr: Int = 0,
    val adjAsr: Int = 0,
    val adjMaghrib: Int = 0,
    val adjIsha: Int = 0,

    // Location (blank timezoneId = follow the device time zone)
    val locationSet: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val cityName: String = "",
    val countryName: String = "",
    val timezoneId: String = "",
    val locationIsAuto: Boolean = false,
    val locationUpdatedAt: Long = 0L,

    // Notifications
    val notifEnabled: Boolean = true,
    val notifFajr: Boolean = true,
    val notifDhuhr: Boolean = true,
    val notifAsr: Boolean = true,
    val notifMaghrib: Boolean = true,
    val notifIsha: Boolean = true,
    val reminderMinutes: Int = 10,
    val notifOnLockScreen: Boolean = true,

    // Azan extras: separate Fajr Azan and user-supplied (licensed) files per Azan slot
    val fajrAzanSource: String = "",
    val userUriMadinah: String? = null,
    val userUriMakkah: String? = null,
    val userUriEgyptian: String? = null,
    val userUriTurkish: String? = null,
) {
    val locationLabel: String
        get() = when {
            !locationSet -> "Location not set"
            cityName.isNotBlank() && countryName.isNotBlank() -> "$cityName, $countryName"
            cityName.isNotBlank() -> cityName
            else -> "%.2f, %.2f".format(latitude, longitude)
        }

    /** Whether the prayer-time alert (notification + Azan) and reminder are on for [p]. */
    fun notifFor(p: Prayer): Boolean = when (p) {
        Prayer.FAJR -> notifFajr
        Prayer.DHUHR -> notifDhuhr
        Prayer.ASR -> notifAsr
        Prayer.MAGHRIB -> notifMaghrib
        Prayer.ISHA -> notifIsha
        Prayer.SUNRISE -> false
    }

    /** The user-supplied audio file for an Azan slot (by AzanSource name), or null. */
    fun azanUri(source: String): String? = when (source) {
        "MADINAH" -> userUriMadinah
        "MAKKAH" -> userUriMakkah
        "EGYPTIAN" -> userUriEgyptian
        "TURKISH" -> userUriTurkish
        "CUSTOM" -> customAzanUri
        else -> null
    }

    companion object {
        const val MIN_DURATION = 1
        const val MAX_DURATION = 60
        fun defaults() = SettingsEntity(id = 1)
    }
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingsEntity)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "notifEnabled INTEGER NOT NULL DEFAULT 1",
            "notifFajr INTEGER NOT NULL DEFAULT 1",
            "notifDhuhr INTEGER NOT NULL DEFAULT 1",
            "notifAsr INTEGER NOT NULL DEFAULT 1",
            "notifMaghrib INTEGER NOT NULL DEFAULT 1",
            "notifIsha INTEGER NOT NULL DEFAULT 1",
            "reminderMinutes INTEGER NOT NULL DEFAULT 10",
            "notifOnLockScreen INTEGER NOT NULL DEFAULT 1",
            "fajrAzanSource TEXT NOT NULL DEFAULT ''",
            "userUriMadinah TEXT",
            "userUriMakkah TEXT",
            "userUriEgyptian TEXT",
            "userUriTurkish TEXT",
        ).forEach { db.execSQL("ALTER TABLE settings ADD COLUMN $it") }
    }
}

@Database(entities = [SettingsEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_prayer.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

class SettingsRepository private constructor(private val dao: SettingsDao) {
    private val mutex = Mutex()

    val settings: Flow<SettingsEntity> = dao.observe().map { it ?: SettingsEntity.defaults() }

    suspend fun current(): SettingsEntity = dao.get() ?: SettingsEntity.defaults()

    suspend fun update(transform: (SettingsEntity) -> SettingsEntity): SettingsEntity =
        mutex.withLock {
            val updated = transform(current()).copy(id = 1)
            dao.upsert(updated)
            updated
        }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(AppDatabase.get(context).settingsDao())
                    .also { instance = it }
            }
    }
}
