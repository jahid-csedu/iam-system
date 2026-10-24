package com.example.iamsystem.client.dto;

import com.example.iamsystem.client.entity.ClientType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientDto(Long id, @JsonProperty("client_id") String clientId, String name, ClientType type) {
}
