package com.tradingplatform.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class UUIDTypeHandlerTest {

    private final UUIDTypeHandler handler = new UUIDTypeHandler();
    private final UUID id = UUID.randomUUID();

    @Test
    void setsUuidAsObjectParameter() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, id, JdbcType.OTHER);
        verify(ps).setObject(1, id);
    }

    @Test
    void readsUuidOrNullFromResultSetByName() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id")).thenReturn(id);
        when(rs.getObject("missing")).thenReturn(null);

        assertEquals(id, handler.getNullableResult(rs, "id"));
        assertNull(handler.getNullableResult(rs, "missing"));
    }

    @Test
    void readsUuidOrNullFromResultSetByIndex() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1)).thenReturn(id);
        when(rs.getObject(2)).thenReturn(null);

        assertEquals(id, handler.getNullableResult(rs, 1));
        assertNull(handler.getNullableResult(rs, 2));
    }

    @Test
    void readsUuidOrNullFromCallableStatement() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getObject(1)).thenReturn(id);
        when(cs.getObject(2)).thenReturn(null);

        assertEquals(id, handler.getNullableResult(cs, 1));
        assertNull(handler.getNullableResult(cs, 2));
    }
}
