package com.example.smsgpstracker.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smsgpstracker.model.GpsTrackPoint

@Dao
interface GpsTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: GpsTrackPoint)

    @Query("SELECT * FROM gps_track ORDER BY timestamp ASC")
    suspend fun getAll(): List<GpsTrackPoint>

    @Query("DELETE FROM gps_track")
    suspend fun clear()
}