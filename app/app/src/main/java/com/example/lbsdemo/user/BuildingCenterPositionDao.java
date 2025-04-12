//package com.example.lbsdemo.user;
//
//import androidx.room.Dao;
//import androidx.room.Insert;
//import androidx.room.OnConflictStrategy;
//import androidx.room.Query;
//
//@Dao
//public interface BuildingCenterPositionDao {
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    void insertAll(BuildingCenterPositionData.BuildingCoord... buildings);
//
//    @Query("SELECT * FROM building_center_position WHERE keywords LIKE '%' || :keyword || '%'")
//    BuildingCenterPositionData.BuildingCoord getBuildingByKeyword(String keyword);
//
//    @Query("SELECT * FROM building_center_position WHERE buildingId = :buildingId")
//    BuildingCenterPositionData.BuildingCoord getBuildingById(String buildingId);
//}