package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.TravelNote;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TravelNoteMapper extends BaseMapper<TravelNote> {

    @Select("SELECT images_json FROM travel_note WHERE images_json IS NOT NULL AND deleted = 0")
    List<String> selectReferencedImageJson();

    @Select("SELECT videos_json FROM travel_note WHERE videos_json IS NOT NULL AND deleted = 0")
    List<String> selectReferencedVideoJson();

    @Select("SELECT content FROM travel_note WHERE content IS NOT NULL AND deleted = 0")
    List<String> selectReferencedContent();
}
