package com.example.livegps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.livegps.data.model.LocationSample

/** A buffered, not-yet-uploaded location fix. */
@Entity(tableName = "buffered_locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val battery: Int?,
    val timestamp: Long,
)

fun LocationSample.toEntity(): LocationEntity = LocationEntity(
    lat = lat, lng = lng, accuracy = accuracy, speed = speed, bearing = bearing,
    battery = battery, timestamp = timestamp,
)

fun LocationEntity.toSample(): LocationSample = LocationSample(
    lat = lat, lng = lng, accuracy = accuracy, speed = speed, bearing = bearing,
    battery = battery, timestamp = timestamp,
)
