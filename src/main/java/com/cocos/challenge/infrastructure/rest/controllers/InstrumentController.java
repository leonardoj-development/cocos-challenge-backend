package com.cocos.challenge.infrastructure.rest.controllers;

import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.domain.ports.in.SearchInstrumentsPort;
import com.cocos.challenge.domain.ports.in.queries.SearchInstrumentsQuery;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.InstrumentSearchResponseDto;
import com.cocos.challenge.infrastructure.rest.controllers.mappers.ControllerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InstrumentController {
    private final SearchInstrumentsPort searchInstruments;
    private final ControllerMapper mapper;

    @GetMapping("/api/v1/instruments")
    public InstrumentSearchResponseDto search(@RequestParam String q,
                                              @RequestParam(defaultValue = "20") int limit,
                                              @RequestParam(defaultValue = "0") int offset) {
        SearchInstrumentsQuery query = SearchInstrumentsQuery.builder()
                .text(q)
                .limit(limit)
                .offset(offset)
                .build();
        List<InstrumentModel> found = searchInstruments.execute(query);
        return mapper.toInstrumentSearchResponse(found, limit, offset);
    }
}
