package org.portfolio.userservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.NoArgsConstructor;
import org.springframework.data.repository.cdi.Eager;

@Builder
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String surname;

    @Column(nullable = false)
    @ToString.Exclude
    private String password;

    @Column(nullable = false,unique = true)
    private String email;

    @Column(nullable = false)
    private boolean deleting;
}
