package com.dreamct.tingfeng.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert
    fun insert(user: User)

    @Query("SELECT * FROM users WHERE account = :account")
    fun getUserByAccount(account: String): User?

    @Query("SELECT * FROM users LIMIT :limit OFFSET :offset")
    fun getUsers(limit: Int, offset: Int): List<User> //调用此处的Java无法消费Flow<List<User>>

    @Query("SELECT COUNT(*) FROM users")
    fun getTotalCount(): Int

    @Delete
    fun delete(user: User)

    @Delete
    fun deleteUsers(users: List<User>)
}