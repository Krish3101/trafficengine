package org.krish.traffic;

import jakarta.persistence.*;

@Entity
@Table(name = "violations1")
public class TrafficViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String vehicleId;
    public double speed;
    public String zone;
    public int fine;
}