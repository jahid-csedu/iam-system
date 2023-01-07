package com.example.management.role;

import com.example.management.user.User;
import lombok.*;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Role {
    @Id
    private String id;
    private String name;
    @ManyToMany(mappedBy = "roles")
    private Set<User> users;
}
