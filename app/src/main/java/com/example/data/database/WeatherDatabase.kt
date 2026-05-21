package com.example.data.database

import android.content.Context
import androidx.room.*
import com.example.data.model.SavedCity
import com.example.data.model.SavedAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCityDao {
    @Query("SELECT * FROM saved_cities ORDER BY timestamp DESC")
    fun getAllCities(): Flow<List<SavedCity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCity(city: SavedCity)

    @Query("DELETE FROM saved_cities WHERE name = :cityName")
    suspend fun deleteCity(cityName: String)

    @Query("UPDATE saved_cities SET isFavorite = :isFav WHERE name = :cityName")
    suspend fun updateFavorite(cityName: String, isFav: Boolean)
}

@Dao
interface SavedAlertDao {
    @Query("SELECT * FROM severe_alerts ORDER BY time DESC")
    fun getAllAlerts(): Flow<List<SavedAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: SavedAlert)

    @Query("DELETE FROM severe_alerts WHERE id = :id")
    suspend fun deleteAlert(id: Long)

    @Query("DELETE FROM severe_alerts")
    suspend fun deleteAllAlerts()
}

@Database(entities = [SavedCity::class, SavedAlert::class], version = 1, exportSchema = false)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun savedCityDao(): SavedCityDao
    abstract fun savedAlertDao(): SavedAlertDao

    companion object {
        @Volatile
        private var INSTANCE: WeatherDatabase? = null

        fun getDatabase(context: Context): WeatherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    "weather_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
