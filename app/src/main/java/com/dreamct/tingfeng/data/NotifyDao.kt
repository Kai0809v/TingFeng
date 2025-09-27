package com.dreamct.tingfeng.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotifyDao {

    /** 插入一条新记录，冲突策略为替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NotificationLog): Long

    /** 更新现有记录 */
    @Update
    suspend fun update(log: NotificationLog)

    /** 获取所有通知记录，并按时间降序排列（最新的在最前面） */
    @Query("SELECT * FROM notification_logs ORDER BY time DESC")
    fun getAllLogs(): Flow<List<NotificationLog>>

    /** 清空整个表 */
    @Query("DELETE FROM notification_logs")
    fun deleteAll(): Int

    /** 根据ID获取单条记录 */
    @Query("SELECT * FROM notification_logs WHERE id = :id")
    suspend fun getLogById(id: Long): NotificationLog?

    /** 获取指定应用名的记录数量 */
    @Query("SELECT COUNT(*) FROM notification_logs WHERE appName = :appName")
    suspend fun getCountByAppName(appName: String): Int

    /** 根据应用包名获取通知记录，按时间降序排列 */
    @Query("SELECT * FROM notification_logs WHERE packageName = :packageName ORDER BY time DESC")
    fun getLogsByPackageName(packageName: String): Flow<List<NotificationLog>>

    /** 根据内容关键词搜索通知记录，按时间降序排列 */
    @Query("SELECT * FROM notification_logs WHERE content LIKE '%' || :keyword || '%' ORDER BY time DESC")
    fun searchLogsByContent(keyword: String): Flow<List<NotificationLog>>

    /** 根据标题关键词搜索通知记录，按时间降序排列 */
    @Query("SELECT * FROM notification_logs WHERE title LIKE '%' || :keyword || '%' ORDER BY time DESC")
    fun searchLogsByTitle(keyword: String): Flow<List<NotificationLog>>

    /** 根据时间范围获取通知记录，按时间降序排列 */
    @Query("SELECT * FROM notification_logs WHERE time BETWEEN :startTime AND :endTime ORDER BY time DESC")
    fun getLogsByTimeRange(startTime: Long, endTime: Long): Flow<List<NotificationLog>>

    /** 获取指定包名在特定时间范围内的通知记录，按时间降序排列 */
    @Query("SELECT * FROM notification_logs WHERE packageName = :packageName AND time BETWEEN :startTime AND :endTime ORDER BY time DESC")
    fun getLogsByPackageAndTime(packageName: String, startTime: Long, endTime: Long): Flow<List<NotificationLog>>

    // 获取最近N条通知记录
    @Query("SELECT * FROM notification_logs ORDER BY time DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<NotificationLog>>

    // 获取指定包名的最近N条通知记录
    @Query("SELECT * FROM notification_logs WHERE packageName = :packageName ORDER BY time DESC LIMIT :limit")
    fun getRecentLogsByPackage(packageName: String, limit: Int): Flow<List<NotificationLog>>

    // 根据ID删除一条记录
    @Query("DELETE FROM notification_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** 根据包名删除记录 */
    @Query("DELETE FROM notification_logs WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String): Int

    /** 获取记录总数 */
    @Query("SELECT COUNT(*) FROM notification_logs")
    suspend fun getCount(): Int

    /** 获取指定包名的记录数量 */
    @Query("SELECT COUNT(*) FROM notification_logs WHERE packageName = :packageName")
    suspend fun getCountByPackage(packageName: String): Int


}