package app.loop.core.database.di

import android.content.Context
import androidx.room.Room
import app.loop.core.database.LoopDatabase
import app.loop.core.database.food.FoodDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LoopDatabase =
        Room.databaseBuilder(context, LoopDatabase::class.java, LoopDatabase.NAME)
            .addMigrations(*LoopDatabase.MIGRATIONS)
            .build()

    @Provides fun profileDao(db: LoopDatabase) = db.profileDao()
    @Provides fun rawEventDao(db: LoopDatabase) = db.rawEventDao()
    @Provides fun sleepSessionDao(db: LoopDatabase) = db.sleepSessionDao()
    @Provides fun workoutDao(db: LoopDatabase) = db.workoutDao()
    @Provides fun mealDao(db: LoopDatabase) = db.mealDao()
    @Provides fun moodCheckInDao(db: LoopDatabase) = db.moodCheckInDao()
    @Provides fun locationCheckInDao(db: LoopDatabase) = db.locationCheckInDao()
    @Provides fun waterLogDao(db: LoopDatabase) = db.waterLogDao()
    @Provides fun healthDao(db: LoopDatabase) = db.healthDao()
    @Provides fun weightLogDao(db: LoopDatabase) = db.weightLogDao()
    @Provides fun goalDao(db: LoopDatabase) = db.goalDao()
    @Provides fun notificationStatDao(db: LoopDatabase) = db.notificationStatDao()
    @Provides fun dailySummaryDao(db: LoopDatabase) = db.dailySummaryDao()
    @Provides fun savedPlaceDao(db: LoopDatabase) = db.savedPlaceDao()
    @Provides fun drinkLogDao(db: LoopDatabase) = db.drinkLogDao()
    @Provides fun journalDao(db: LoopDatabase) = db.journalDao()
    @Provides fun customFoodDao(db: LoopDatabase) = db.customFoodDao()
    @Provides fun cycleDayDao(db: LoopDatabase) = db.cycleDayDao()

    @Provides
    @Singleton
    fun foodDatabase(@ApplicationContext context: Context): FoodDatabase =
        Room.databaseBuilder(context, FoodDatabase::class.java, FoodDatabase.NAME)
            .createFromAsset(FoodDatabase.ASSET)
            // The food list is read-only reference data: a newer bundled version simply replaces it.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun foodDao(db: FoodDatabase) = db.foodDao()
}
