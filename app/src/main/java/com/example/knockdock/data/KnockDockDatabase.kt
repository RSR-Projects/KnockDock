package com.example.knockdock.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.example.knockdock.model.ActionDefinition
import com.example.knockdock.model.ActionStep
import com.example.knockdock.model.ActionType
import com.example.knockdock.model.DockProfile
import com.example.knockdock.model.GestureCategory
import com.example.knockdock.model.GestureDefinition
import com.example.knockdock.model.GestureType
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// ---------------- ENTITIES ----------------

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val createdAtEpochMs: Long
)

@Entity(tableName = "gestures")
data class GestureEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String,
    val category: String,
    val expectedImpacts: Int,
    val minIntervalMs: Long,
    val maxIntervalMs: Long,
    val minConfidence: Float,
    val cooldownMs: Long,
    val isSystemPreset: Boolean,
    val trainingTrialCount: Int,
    val templateIntervalsJson: String // Serialized JSON array of Longs
)

@Entity(tableName = "actions")
data class ActionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String,
    val isEnabled: Boolean,
    val parametersJson: String, // Serialized JSON object
    val stepsJson: String, // Serialized JSON array of steps
    val createdAtEpochMs: Long
)

@Entity(tableName = "mappings")
data class MappingEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val gestureId: String,
    val actionId: String,
    val isEnabled: Boolean
)

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val actionId: String,
    val actionName: String,
    val gestureName: String,
    val success: Boolean,
    val message: String,
    val executedAtEpochMs: Long,
    val isSimulated: Boolean
)

// ---------------- DAOS ----------------

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isDefault DESC, name ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Query("UPDATE profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(profileId: String)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: String)
}

@Dao
interface GestureDao {
    @Query("SELECT * FROM gestures ORDER BY isSystemPreset DESC, name ASC")
    fun getAllGestures(): Flow<List<GestureEntity>>

    @Query("SELECT * FROM gestures WHERE id = :id LIMIT 1")
    suspend fun getGestureById(id: String): GestureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGesture(gesture: GestureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGestures(gestures: List<GestureEntity>)

    @Delete
    suspend fun deleteGesture(gesture: GestureEntity)

    @Query("DELETE FROM gestures WHERE id = :id AND isSystemPreset = 0")
    suspend fun deleteCustomGestureById(id: String)
}

@Dao
interface ActionDao {
    @Query("SELECT * FROM actions ORDER BY name ASC")
    fun getAllActions(): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE id = :id LIMIT 1")
    suspend fun getActionById(id: String): ActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<ActionEntity>)

    @Delete
    suspend fun deleteAction(action: ActionEntity)

    @Query("DELETE FROM actions WHERE id = :id")
    suspend fun deleteActionById(id: String)
}

@Dao
interface MappingDao {
    @Query("SELECT * FROM mappings WHERE profileId = :profileId")
    fun getMappingsForProfile(profileId: String): Flow<List<MappingEntity>>

    @Query("SELECT * FROM mappings")
    fun getAllMappings(): Flow<List<MappingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: MappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMappings(mappings: List<MappingEntity>)

    @Delete
    suspend fun deleteMapping(mapping: MappingEntity)

    @Query("DELETE FROM mappings WHERE id = :id")
    suspend fun deleteMappingById(id: String)

    @Query("DELETE FROM mappings WHERE profileId = :profileId")
    suspend fun deleteMappingsByProfileId(profileId: String)
}

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY executedAtEpochMs DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLogEntity)

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()
}

// ---------------- DATABASE ----------------

@Database(
    entities = [
        ProfileEntity::class,
        GestureEntity::class,
        ActionEntity::class,
        MappingEntity::class,
        ExecutionLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KnockDockDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun gestureDao(): GestureDao
    abstract fun actionDao(): ActionDao
    abstract fun mappingDao(): MappingDao
    abstract fun executionLogDao(): ExecutionLogDao
}
