package com.example.iamsystem.user.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserUpdateDtoTest {

    @Test
    @DisplayName("The audit log gets the names of the requested fields, never their values")
    void fields_namesTheRequestedFields() {
        assertEquals(List.of("fullName", "email"), new UserUpdateDto("Robert", "robert@example.com").fields());
        assertEquals(List.of("email"), new UserUpdateDto(null, "robert@example.com").fields());
        assertEquals(List.of(), new UserUpdateDto(null, null).fields());
    }
}
