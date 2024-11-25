package com.airbnb.model;


import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
    
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private String guestName;
    private String guestEmail;
    private Integer numberOfGuests;
    private BigDecimal totalPrice;
    private BigDecimal occupancyTax;
    private String status; // PENDING, CONFIRMED, CANCELLED, COMPLETED
}
