package com.cocos.challenge.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "userid")
    private Integer userId;
    @Column(name = "instrumentid")
    private Integer instrumentId;
    private String side;
    private BigDecimal size;
    private BigDecimal price;
    private String type;
    private String status;
    private LocalDateTime datetime;
}
