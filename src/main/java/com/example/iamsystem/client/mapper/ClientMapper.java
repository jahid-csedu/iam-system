package com.example.iamsystem.client.mapper;

import com.example.iamsystem.client.dto.ClientDto;
import com.example.iamsystem.client.dto.TokenSettingsDto;
import com.example.iamsystem.client.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    @Mapping(target = "tokenSettings", source = ".")
    ClientDto toDto(Client client);

    List<ClientDto> toDtoList(List<Client> clients);

    @Mapping(target = "claims", source = "tokenClaims")
    TokenSettingsDto toTokenSettings(Client client);
}
