package com.cocos.challenge.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "marketdata")
@Getter
@Setter
@NoArgsConstructor
public class MarketDataEntity {
    @Id
    private Integer id;
    @Column(name = "instrumentid")
    private Integer instrumentId;
    private BigDecimal close;
    @Column(name = "previousclose")
    private BigDecimal previousClose;
    private LocalDate date;
}
