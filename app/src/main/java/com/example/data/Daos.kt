package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyConfigDao {
    @Query("SELECT * FROM api_key_configs ORDER BY priority ASC, id ASC")
    fun getAllConfigsFlow(): Flow<List<ApiKeyConfig>>

    @Query("SELECT * FROM api_key_configs ORDER BY priority ASC, id ASC")
    suspend fun getAllConfigs(): List<ApiKeyConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ApiKeyConfig): Long

    @Update
    suspend fun updateConfig(config: ApiKeyConfig)

    @Delete
    suspend fun deleteConfig(config: ApiKeyConfig)
}

@Dao
interface ProjectStateDao {
    @Query("SELECT * FROM project_states ORDER BY lastModifiedTimeMs DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectState>>

    @Query("SELECT * FROM project_states WHERE folderUri = :uri LIMIT 1")
    suspend fun getProjectByUri(uri: String): ProjectState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectState)

    @Update
    suspend fun updateProject(project: ProjectState)

    @Query("DELETE FROM project_states WHERE folderUri = :uri")
    suspend fun deleteProject(uri: String)
}

@Dao
interface SrtLineStateDao {
    @Query("SELECT * FROM srt_line_states WHERE folderUri = :folderUri ORDER BY lineIndex ASC")
    fun getLineStatesForProjectFlow(folderUri: String): Flow<List<SrtLineState>>

    @Query("SELECT * FROM srt_line_states WHERE folderUri = :folderUri ORDER BY lineIndex ASC")
    suspend fun getLineStatesForProject(folderUri: String): List<SrtLineState>

    @Query("SELECT * FROM srt_line_states WHERE id = :id LIMIT 1")
    suspend fun getLineStateById(id: String): SrtLineState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineState(state: SrtLineState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineStates(states: List<SrtLineState>)

    @Query("DELETE FROM srt_line_states WHERE folderUri = :folderUri")
    suspend fun deleteLineStatesForProject(folderUri: String)
}

@Dao
interface GeneralSettingDao {
    @Query("SELECT * FROM general_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): GeneralSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: GeneralSetting)

    @Query("SELECT value FROM general_settings WHERE `key` = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<String?>
}
