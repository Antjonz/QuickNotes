package com.anton.quicknotes2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, Folder::class, NoteImage::class, Whiteboard::class], version = 7, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun noteImageDao(): NoteImageDao
    abstract fun whiteboardDao(): WhiteboardDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `folderId` INTEGER DEFAULT NULL REFERENCES `folders`(`id`) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_images` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`noteId` INTEGER NOT NULL, " +
                    "`uri` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_images_noteId` ON `note_images` (`noteId`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `iconUri` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `iconUri` TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `whiteboards` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`strokesJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`folderId` INTEGER DEFAULT NULL, " +
                    "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                    "`iconUri` TEXT DEFAULT NULL, " +
                    "FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboards_folderId` ON `whiteboards` (`folderId`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `parentFolderId` INTEGER DEFAULT NULL REFERENCES `folders`(`id`) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentFolderId` ON `folders` (`parentFolderId`)")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
