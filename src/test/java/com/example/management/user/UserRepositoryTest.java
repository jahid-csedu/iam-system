package com.example.management.user;

import com.example.management.user.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.annotation.Rollback;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Rollback(value = false)
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository repository;

    @Test
    void testCreateUser() {
//        User user = new User();
//        user.setId(UUID.randomUUID().toString());
//        user.setUsername("jahid");
//        user.setPassword("12345");
//        user.setFullName("Md. Jahid Hasan");
//        user.setEmail("jahid.csedu@gmail.com");
//        user.setActive(true);
//        user.setPasswordExpired(false);
//
//        User savedUser = repository.save(user);
//        User existsUser = entityManager.find(User.class, savedUser.getId());
//
//        assertThat(user.getUsername()).isEqualTo(existsUser.getUsername());
    }

    @Test
    void findByUsername() {
        Optional<User> exists = repository.findByUsername("jahid");
        assertThat(exists.isPresent()).isTrue();
        exists = repository.findByUsername("abcd");
        assertThat(exists.isEmpty()).isTrue();
    }

    @Test
    void findByEmail() {
        Optional<User> exists = repository.findByEmail("jahid.csedu@gmail.com");
        assertThat(exists.isPresent()).isTrue();
    }
}