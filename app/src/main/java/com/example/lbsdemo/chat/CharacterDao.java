package com.example.lbsdemo.chat;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * CharacterDao接口
 * 定义对虚拟角色表的数据库操作
 */
@Dao
public interface CharacterDao {
    
    /**
     * 插入新角色，如果ID已存在则替换
     * @param character 虚拟角色
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCharacter(Character character);
    
    /**
     * 插入新角色的别名方法，与insertCharacter功能相同
     * @param character 虚拟角色
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Character character);
    
    /**
     * 批量插入角色
     * @param characters 虚拟角色列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCharacters(List<Character> characters);
    
    /**
     * 更新角色信息
     * @param character 虚拟角色
     */
    @Update
    void updateCharacter(Character character);
    
    /**
     * 根据ID获取角色
     * @param id 角色ID
     * @return 角色信息
     */
    @Query("SELECT * FROM characters WHERE id = :id")
    Character getCharacterById(String id);
    
    /**
     * 获取所有角色
     * @return 所有角色列表
     */
    @Query("SELECT * FROM characters")
    List<Character> getAllCharacters();
    
    /**
     * 删除指定角色
     * @param id 角色ID
     * @return 删除的数量
     */
    @Query("DELETE FROM characters WHERE id = :id")
    int deleteCharacter(String id);
    
    /**
     * 检查角色是否存在
     * @param id 角色ID
     * @return 如果存在则返回1，否则返回0
     */
    @Query("SELECT COUNT(*) FROM characters WHERE id = :id")
    int characterExists(String id);
} 