package com.alarmapp.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Database(
    entities = [
        ProfileEntity::class,
        AlarmEntity::class,
        SleepSessionEntity::class,
        IndicatorEntity::class,
        RatingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao

    companion object {
        private const val DB_NAME = "alarmapp.db"

        /** v2: varias alarmas con días de la semana. Conserva los datos existentes. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm ADD COLUMN days INTEGER NOT NULL DEFAULT 127")
            }
        }

        /**
         * Base de datos cifrada con SQLCipher (RNF5). La clave es aleatoria y se guarda
         * envuelta con una llave del Android Keystore, que nunca sale del hardware seguro.
         */
        fun create(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKey.passphrase(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}

/** Genera y protege la clave de la base de datos. */
internal object DatabaseKey {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "alarmapp_db_key"
    private const val PREFS = "alarmapp_secure"
    private const val PREF_WRAPPED = "wrapped_passphrase"
    private const val GCM_TAG_BITS = 128

    fun passphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREF_WRAPPED, null)
        if (stored != null) {
            runCatching { return unwrap(stored) }
            // La llave del Keystore se perdió (p. ej. tras restaurar el sistema): los datos
            // cifrados ya no se pueden leer, así que se empieza de cero de forma segura.
            context.deleteDatabase("alarmapp.db")
        }
        return create(prefs)
    }

    private fun create(prefs: SharedPreferences): ByteArray {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(raw)
        val blob = cipher.iv + encrypted
        prefs.edit().putString(PREF_WRAPPED, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        return raw
    }

    private fun unwrap(stored: String): ByteArray {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, 12)
        val data = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(data)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
