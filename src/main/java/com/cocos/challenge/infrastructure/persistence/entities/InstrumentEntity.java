package com.cocos.challenge.infrastructure.persistence.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "instruments")
@Getter
@Setter
@NoArgsConstructor
public class InstrumentEntity {
    @Id
    private Integer id;
    private String ticker;
    private String name;
    private String type;
}
