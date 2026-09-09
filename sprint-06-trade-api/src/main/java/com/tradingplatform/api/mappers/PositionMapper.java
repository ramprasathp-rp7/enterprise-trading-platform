import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.dto.PositionResponse;

interface PositionMapper {
    @Select("SELECT id, symbol, quantity, average_cost FROM accounts where id=#{id}")
    PositionResponse findPositionByAccountId(int id);
}
