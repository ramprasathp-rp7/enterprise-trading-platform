package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.model.Instrument;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InstrumentService {

    private final InstrumentMapper instrumentMapper;

    public InstrumentService(InstrumentMapper instrumentMapper) {
        this.instrumentMapper = instrumentMapper;
    }

    public Instrument getInstrumentById(Long id) {
        return instrumentMapper.findById(id);
    }

    public List<Instrument> getAllInstruments() {
        List<Instrument> instruments = instrumentMapper.findAllInstruments();
        return instruments != null ? instruments : List.of();
    }

    public List<Instrument> getTradableInstruments() {
        List<Instrument> instruments = instrumentMapper.findTradableInstruments();
        return instruments != null ? instruments : List.of();
    }
}
