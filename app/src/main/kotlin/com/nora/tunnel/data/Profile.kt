package com.nora.tunnel.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,          // vless, vmess, trojan, ss, wireguard
    val address: String,
    val port: Int,
    val secret: String,            // uuid / password
    val transport: String = "tcp", // tcp, ws, grpc, h2, httpupgrade, xhttp
    val security: String = "none", // none, tls, reality
    val extra: String = "{}",      // JSON: path, host, sni, flow, pbk, sid ...
    val country: String = "",
    val rawLink: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): Profile?

    @Insert suspend fun insert(p: Profile): Long
    @Update suspend fun update(p: Profile)
    @Delete suspend fun delete(p: Profile)
}

@Database(entities = [Profile::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun profiles(): ProfileDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "nora.db")
                .build().also { inst = it }
        }
    }
}
