package com.tradingplatform.instruments.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.instruments.model.InstrumentListing;
import com.tradingplatform.instruments.service.InstrumentCatalogService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The instrument catalogue (contract: instruments-api.yaml).
 *
 * Any signed-in user may read it and run a discovery: instruments are reference data, not account
 * data, so there is no account in the path.
 */
@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentCatalogController {

    private final InstrumentCatalogService catalogService;

    public InstrumentCatalogController(InstrumentCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** GET /api/v1/instruments: every listed instrument, in id order */
    @GetMapping
    public ResponseEntity<List<InstrumentResponse>> listInstruments() {
        return ResponseEntity.ok(catalogService.list().stream().map(InstrumentResponse::from).toList());
    }

    /**
     * POST /api/v1/instruments/discover: lists a symbol Fauxnance can quote. 201 when this call added
     * it, 200 when it was already listed.
     */
    @PostMapping("/discover")
    public ResponseEntity<DiscoveredInstrumentResponse> discoverInstrument(@Valid @RequestBody DiscoverInstrumentRequest request) {
        InstrumentCatalogService.Discovery discovery = catalogService.discover(request.symbol());
        return ResponseEntity.status(discovery.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(new DiscoveredInstrumentResponse(InstrumentResponse.from(discovery.instrument()), discovery.created()));
    }

    public record DiscoverInstrumentRequest(@NotBlank @Size(max = 20) String symbol) {
    }

    public record InstrumentResponse(long id, String symbol, String name, String assetClass, String currency,
                                     String exchange, boolean tradable) {
        static InstrumentResponse from(InstrumentListing i) {
            return new InstrumentResponse(i.getId(), i.getSymbol(), i.getName(), i.getAssetClass(), i.getCurrency(),
                i.getExchange(), i.isTradable());
        }
    }

    public record DiscoveredInstrumentResponse(InstrumentResponse instrument, boolean created) {
    }
}
