package org.esoalahy.stock.modules.depot;

import org.esoalahy.stock.core.jpa.Horodate;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "depots")
public class Depot extends Horodate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(length = 255)
    private String adresse;

    @Column(nullable = false)
    private boolean actif = true;
}
