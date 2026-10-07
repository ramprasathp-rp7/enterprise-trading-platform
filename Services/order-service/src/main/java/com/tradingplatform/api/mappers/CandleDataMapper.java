package com.tradingplatform.api.mappers;

import com.tradingplatform.domain.model.CandleData;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface CandleDataMapper {

    @Insert("""
        INSERT INTO candle_data (instrument_id, date, open, high, low, close, adjclose, volume, synthetic)
        VALUES (#{instrumentId}, #{date}, #{open}, #{high}, #{low}, #{close}, #{adjClose}, #{volume}, #{synthetic})
        ON CONFLICT (instrument_id, date)
        DO UPDATE SET
            open = EXCLUDED.open,
            high = EXCLUDED.high,
            low = EXCLUDED.low,
            close = EXCLUDED.close,
            adjclose = EXCLUDED.adjclose,
            volume = EXCLUDED.volume,
            synthetic = EXCLUDED.synthetic,
            updated_at = now()
        """)
    void upsertCandle(CandleData candle);

    @Insert("""
        <script>
            INSERT INTO candle_data (instrument_id, date, open, high, low, close, adjclose, volume, synthetic)
            VALUES
            <foreach item='c' collection='candles' separator=','>
                (#{c.instrumentId}, #{c.date}, #{c.open}, #{c.high}, #{c.low}, #{c.close}, #{c.adjClose}, #{c.volume}, #{c.synthetic})
            </foreach>
            ON CONFLICT (instrument_id, date)
            DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                adjclose = EXCLUDED.adjclose,
                volume = EXCLUDED.volume,
                synthetic = EXCLUDED.synthetic,
                updated_at = now()
        </script>
        """)
    void upsertBatch(@Param("candles") List<CandleData> candles);

    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "date", javaType = LocalDate.class),
        @Arg(column = "open", javaType = java.math.BigDecimal.class),
        @Arg(column = "high", javaType = java.math.BigDecimal.class),
        @Arg(column = "low", javaType = java.math.BigDecimal.class),
        @Arg(column = "close", javaType = java.math.BigDecimal.class),
        @Arg(column = "adjclose", javaType = java.math.BigDecimal.class),
        @Arg(column = "volume", javaType = Long.class),
        @Arg(column = "synthetic", javaType = boolean.class),
        @Arg(column = "created_at", javaType = java.time.Instant.class),
        @Arg(column = "updated_at", javaType = java.time.Instant.class)
    })
    @Select("""
        SELECT id, instrument_id, date, open, high, low, close, adjclose, volume, synthetic, created_at, updated_at
        FROM candle_data
        WHERE instrument_id = #{instrumentId} AND date = #{date}
        """)
    CandleData selectByInstrumentAndDate(@Param("instrumentId") Long instrumentId, @Param("date") LocalDate date);

    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "date", javaType = LocalDate.class),
        @Arg(column = "open", javaType = java.math.BigDecimal.class),
        @Arg(column = "high", javaType = java.math.BigDecimal.class),
        @Arg(column = "low", javaType = java.math.BigDecimal.class),
        @Arg(column = "close", javaType = java.math.BigDecimal.class),
        @Arg(column = "adjclose", javaType = java.math.BigDecimal.class),
        @Arg(column = "volume", javaType = Long.class),
        @Arg(column = "synthetic", javaType = boolean.class),
        @Arg(column = "created_at", javaType = java.time.Instant.class),
        @Arg(column = "updated_at", javaType = java.time.Instant.class)
    })
    @Select("""
        SELECT id, instrument_id, date, open, high, low, close, adjclose, volume, synthetic, created_at, updated_at
        FROM candle_data
        WHERE instrument_id = #{instrumentId} AND date BETWEEN #{from} AND #{to}
        ORDER BY date ASC
        """)
    List<CandleData> selectByInstrumentDateRange(@Param("instrumentId") Long instrumentId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "date", javaType = LocalDate.class),
        @Arg(column = "open", javaType = java.math.BigDecimal.class),
        @Arg(column = "high", javaType = java.math.BigDecimal.class),
        @Arg(column = "low", javaType = java.math.BigDecimal.class),
        @Arg(column = "close", javaType = java.math.BigDecimal.class),
        @Arg(column = "adjclose", javaType = java.math.BigDecimal.class),
        @Arg(column = "volume", javaType = Long.class),
        @Arg(column = "synthetic", javaType = boolean.class),
        @Arg(column = "created_at", javaType = java.time.Instant.class),
        @Arg(column = "updated_at", javaType = java.time.Instant.class)
    })
    @Select("""
        SELECT id, instrument_id, date, open, high, low, close, adjclose, volume, synthetic, created_at, updated_at
        FROM candle_data
        WHERE instrument_id = #{instrumentId}
        ORDER BY date DESC
        LIMIT #{count}
        """)
    List<CandleData> selectLatestByInstrument(@Param("instrumentId") Long instrumentId, @Param("count") int count);

    @Delete("DELETE FROM candle_data WHERE instrument_id = #{instrumentId} AND date = #{date}")
    void deleteByInstrumentAndDate(@Param("instrumentId") Long instrumentId, @Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM candle_data WHERE instrument_id = #{instrumentId} AND date = #{date}")
    long countByInstrumentAndDate(@Param("instrumentId") Long instrumentId, @Param("date") LocalDate date);

    @Select("SELECT deleted_count FROM purge_old_candle_data()")
    Long purgeOldData();
}
