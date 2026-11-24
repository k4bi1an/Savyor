package com.savyor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.savyor.app.data.local.entity.CategoryCorrectionEntity
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.local.entity.ReelFtsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ReelEntity::class,
        ReelFtsEntity::class,
        CategoryEntity::class,
        CategoryCorrectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SavyorDatabase : RoomDatabase() {

    abstract fun reelDao(): ReelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryCorrectionDao(): CategoryCorrectionDao

    companion object {
        @Volatile
        private var INSTANCE: SavyorDatabase? = null

        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(name = "Tech & Code", isDefault = true, displayOrder = 1),
            CategoryEntity(name = "Recipes & Cooking", isDefault = true, displayOrder = 2),
            CategoryEntity(name = "Health & Fitness", isDefault = true, displayOrder = 3),
            CategoryEntity(name = "Design & UI", isDefault = true, displayOrder = 4),
            CategoryEntity(name = "Other", isDefault = true, displayOrder = 5)
        )

        fun getInstance(context: Context): SavyorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SavyorDatabase::class.java,
                    "savyor.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).categoryDao().insertAll(DEFAULT_CATEGORIES)
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
