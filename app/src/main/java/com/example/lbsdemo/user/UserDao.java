package com.example.lbsdemo.user;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertUser(User user); // 学号主键冲突时终止[^23]

    @Query("SELECT * FROM users WHERE studentId = :studId")
    User getUserById(String studId);

    @Query("SELECT * FROM users WHERE username = :username")
    User getUserByUsername(String username); // 支持用户名查询[^2]

    @Query("SELECT * FROM users")
    List<User> getAllUsers();

    @Update
    void updatePassword(User user); // 密码更新功能[^26]
}