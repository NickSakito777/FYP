package com.example.lbsdemo.user;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    public String studentId; // 学号为主键[^8]

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password")
    public String password;

    @ColumnInfo(name = "register_time", defaultValue = "CURRENT_TIMESTAMP")
    public long registerTime;

    public User(@NonNull String studentId, String username, String password){
        this.studentId = studentId;
        this.username = username;
        this.password = password;
    }
}
