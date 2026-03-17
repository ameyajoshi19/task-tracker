package com.tasktracker.data.local.converter

import androidx.room.TypeConverter
import com.tasktracker.domain.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): Int? = value?.value

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? = value?.let { DayOfWeek.of(it) }

    @TypeConverter
    fun fromQuadrant(value: Quadrant?): String? = value?.name

    @TypeConverter
    fun toQuadrant(value: String?): Quadrant? = value?.let { Quadrant.valueOf(it) }

    @TypeConverter
    fun fromDayPreference(value: DayPreference?): String? = value?.name

    @TypeConverter
    fun toDayPreference(value: String?): DayPreference? = value?.let { DayPreference.valueOf(it) }

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus?): String? = value?.name

    @TypeConverter
    fun toTaskStatus(value: String?): TaskStatus? = value?.let { TaskStatus.valueOf(it) }

    @TypeConverter
    fun fromBlockStatus(value: BlockStatus?): String? = value?.name

    @TypeConverter
    fun toBlockStatus(value: String?): BlockStatus? = value?.let { BlockStatus.valueOf(it) }

    @TypeConverter
    fun fromSyncOperationType(value: SyncOperationType?): String? = value?.name

    @TypeConverter
    fun toSyncOperationType(value: String?): SyncOperationType? =
        value?.let { SyncOperationType.valueOf(it) }
}
