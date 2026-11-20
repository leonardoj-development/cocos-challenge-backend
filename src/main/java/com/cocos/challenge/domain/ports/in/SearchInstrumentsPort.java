package com.cocos.challenge.domain.ports.in;

import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.domain.ports.in.queries.SearchInstrumentsQuery;

import java.util.List;

public interface SearchInstrumentsPort {
    List<InstrumentModel> execute(SearchInstrumentsQuery query);
}
