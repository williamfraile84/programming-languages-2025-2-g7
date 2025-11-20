package com.jukhg10.hanzimemo.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Character::class, Word::class, StudyItem::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun characterDao(): CharacterDao
    abstract fun wordDao(): WordDao
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ✅ MIGRACIÓN: Agregar índices para mejor rendimiento de búsqueda
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d("AppDatabase", "🔧 Ejecutando migración 3 → 4: Creando índices...")
                try {
                    // Índices para búsqueda por pinyin (más común en Dictionary)
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_character_pinyin ON characters(pinyin)")
                    Log.d("AppDatabase", "✓ Índice creado: idx_character_pinyin")
                    
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_pinyin ON words(pinyin)")
                    Log.d("AppDatabase", "✓ Índice creado: idx_word_pinyin")
                    
                    // Índices para búsqueda por definición
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_character_definition ON characters(definition)")
                    Log.d("AppDatabase", "✓ Índice creado: idx_character_definition")
                    
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_definition ON words(definition)")
                    Log.d("AppDatabase", "✓ Índice creado: idx_word_definition")
                    
                    // Índices para estudio y revisión
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_learned ON study_items(learned)")
                    Log.d("AppDatabase", "✓ Índice creado: idx_study_learned")
                    
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_type ON study_items(itemType)")
                    Log.d("AppDatabase", "✓ Índice creado: idx_study_type")
                    
                    Log.d("AppDatabase", "✅ Migración 3 → 4 completada: 6 índices creados")
                } catch (e: Exception) {
                    Log.e("AppDatabase", "❌ Error en migración: ${e.message}", e)
                    throw e
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "hanzi.db"
                    )
                        .createFromAsset("hanzi.db")
                        .addMigrations(MIGRATION_3_4)  // ✅ Agregar migración
                        .setJournalMode(JournalMode.TRUNCATE)
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}