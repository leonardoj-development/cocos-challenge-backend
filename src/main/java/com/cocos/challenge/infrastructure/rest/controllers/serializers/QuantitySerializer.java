package com.cocos.challenge.infrastructure.rest.controllers.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;

public class QuantitySerializer extends StdSerializer<BigDecimal> {
    public QuantitySerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeNumber(value.stripTrailingZeros().toPlainString());
    }
}
