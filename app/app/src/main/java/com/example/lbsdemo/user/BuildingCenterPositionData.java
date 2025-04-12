//package com.example.lbsdemo.user;
//
//import androidx.room.ColumnInfo;
//import androidx.room.Entity;
//import androidx.room.PrimaryKey;
//
//public class BuildingCenterPositionData {
//    @Entity(tableName = "building_center_position")
//    public class BuildingCoord {
//        @PrimaryKey
//        public String buildingId;
//
//        @ColumnInfo(name = "keywords")
//        public String keywords;  // 支持多个关键词，用"|"分隔
//
//        @ColumnInfo
//        public double latitude;
//
//        @ColumnInfo
//        public double longitude;
//    }
//}
