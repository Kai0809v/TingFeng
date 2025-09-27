package com.dreamct.tingfeng.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @field:ColumnInfo(name = "account")
    @JvmField // 添加这个注解@JvmField用于Java操作
    val account: String,

    @field:ColumnInfo(name = "password")
    @JvmField // 添加这个注解@JvmField用于Java操作
    val password: String // 实际中应存储哈希值
) {
    @PrimaryKey(autoGenerate = true)
    @field:ColumnInfo(name = "id")
    @JvmField // 添加这个注解@JvmField用于Java操作
    var id: Int = 0
        //private set

    //可以移除setId方法，因为@JvmField使字段可直接访问
//    fun setId(id: Int) {
//        this.id = id
//    }
}