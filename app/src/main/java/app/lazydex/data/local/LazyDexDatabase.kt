package app.lazydex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity

@Database(entities = [MediaItemEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
}
