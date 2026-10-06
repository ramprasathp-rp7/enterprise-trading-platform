package com.tradingplatform.api.mappers;

import com.tradingplatform.domain.model.CandleSyncWatermark;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface CandleWatermarkMapper {

    @Insert("""
        INSERT INTO candle_sync_watermarks (instrument_id, last_synced_date, sync_status, bootstrap_completed)
        VALUES (#{instrumentId}, #{lastSyncedDate}, #{syncStatus}, #{bootstrapCompleted})
        """)
    void insertWatermark(CandleSyncWatermark watermark);

    @Update("""
        UPDATE candle_sync_watermarks
        SET last_synced_date = #{lastSyncedDate},
            sync_status = #{syncStatus},
            last_sync_time = now(),
            bootstrap_completed = #{bootstrapCompleted}
        WHERE instrument_id = #{instrumentId}
        """)
    void updateWatermark(CandleSyncWatermark watermark);

    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "last_synced_date", javaType = LocalDate.class),
        @Arg(column = "sync_status", javaType = String.class),
        @Arg(column = "last_sync_time", javaType = java.time.Instant.class),
        @Arg(column = "bootstrap_completed", javaType = boolean.class)
    })
    @Select("""
        SELECT id, instrument_id, last_synced_date, sync_status, last_sync_time, bootstrap_completed
        FROM candle_sync_watermarks
        WHERE instrument_id = #{instrumentId}
        """)
    CandleSyncWatermark selectByInstrumentId(@Param("instrumentId") Long instrumentId);

    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "last_synced_date", javaType = LocalDate.class),
        @Arg(column = "sync_status", javaType = String.class),
        @Arg(column = "last_sync_time", javaType = java.time.Instant.class),
        @Arg(column = "bootstrap_completed", javaType = boolean.class)
    })
    @Select("""
        SELECT id, instrument_id, last_synced_date, sync_status, last_sync_time, bootstrap_completed
        FROM candle_sync_watermarks
        WHERE sync_status = 'FAILED'
        """)
    List<CandleSyncWatermark> selectFailedSyncs();
}
