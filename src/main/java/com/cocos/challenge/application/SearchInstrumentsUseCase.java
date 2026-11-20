package com.cocos.challenge.application;

import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.domain.ports.in.SearchInstrumentsPort;
import com.cocos.challenge.domain.ports.in.queries.SearchInstrumentsQuery;
import com.cocos.challenge.domain.ports.out.InstrumentPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchInstrumentsUseCase implements SearchInstrumentsPort {
    private final InstrumentPersistencePort instruments;

    @Override
    public List<InstrumentModel> execute(SearchInstrumentsQuery query) {
        return instruments.search(query.text(), query.limit(), query.offset());
    }
}
