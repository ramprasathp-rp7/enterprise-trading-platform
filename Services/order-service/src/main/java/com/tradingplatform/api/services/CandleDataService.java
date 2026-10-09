package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.exception.CandleDataException;
import com.tradingplatform.domain.exception.CandleSyncException;
import com.tradingplatform.domain.model.CandleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandleDataService {

    private static final Logger log = LoggerFactory.getLogger(CandleDataService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public CandleDataService(RestTemplate restTemplate,
                             ObjectMapper objectMapper,
                             @Value("${FAUXNANCE_BASE_URL:${fauxnance.url}}") String baseUrl,
                             @Value("${FAUXNANCE_API_KEY:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public List<CandleData> fetchCandlesForSymbol(Long instrumentId, String symbol, LocalDate from, LocalDate to) {
        validateDateRange(from, to);

        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        String url = String.format("%s/candles/%s?from=%s&to=%s&interval=1d", baseUrl, encodedSymbol, from, to);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new CandleSyncException("Failed to fetch candles for symbol " + symbol + ": " + response.getStatusCode());
            }

            return parseCandlesResponse(instrumentId, response.getBody());
        } catch (CandleDataException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error fetching candles for symbol {}", symbol, ex);
            throw new CandleSyncException("Failed to fetch candles for symbol " + symbol, ex);
        }
    }

    public void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new CandleDataException("from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new CandleDataException("from date must be before or equal to to date");
        }
        if (ChronoUnit.DAYS.between(from, to) > 3650) {
            throw new CandleDataException("Date range cannot exceed 10 years");
        }
    }

    List<CandleData> parseCandlesResponse(Long instrumentId, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candlesNode = root.path("data").path("candles");
            if (!candlesNode.isArray()) {
                candlesNode = root.path("candles");
            }

            if (!candlesNode.isArray()) {
                return List.of();
            }

            List<CandleData> candles = new ArrayList<>();
            for (JsonNode item : candlesNode) {
                LocalDate date = LocalDate.parse(item.path("date").asText());
                BigDecimal open = new BigDecimal(item.path("open").asText());
                BigDecimal high = new BigDecimal(item.path("high").asText());
                BigDecimal low = new BigDecimal(item.path("low").asText());
                BigDecimal close = new BigDecimal(item.path("close").asText());
                String adjText = item.hasNonNull("adjclose") ? item.path("adjclose").asText() : item.path("adjClose").asText();
                BigDecimal adjClose = new BigDecimal(adjText);
                long volume = item.path("volume").asLong(0);
                boolean synthetic = item.path("synthetic").asBoolean(false);

                candles.add(new CandleData(instrumentId, date, open, high, low, close, adjClose, volume, synthetic));
            }

            candles.sort(java.util.Comparator.comparing(CandleData::getDate));
            return candles;
        } catch (Exception ex) {
            throw new CandleDataException("Failed to parse candle response", ex);
        }
    }
}
