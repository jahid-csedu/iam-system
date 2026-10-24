package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.example.iamsystem.user.service.UserTestData.createdBy;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserService service;
    private User root;
    private User child;

    @BeforeEach
    void setUp() {
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        service = new UserService(userRepository, accessPolicy, currentUserProvider, userMapper);
        root = rootUser(1, "root");
        child = createdBy(root, 2, "child");
        when(currentUserProvider.requireCurrentUser()).thenReturn(root);
    }

    @Test
    void findAllUsers_returnsOnlyVisibleUsers() {
        User stranger = user(3, "stranger");
        when(userRepository.findAll()).thenReturn(List.of(root, child, stranger));
        when(accessPolicy.canView(root, root)).thenReturn(true);
        when(accessPolicy.canView(root, child)).thenReturn(true);
        when(accessPolicy.canView(root, stranger)).thenReturn(false);

        List<UserDto> result = service.findAllUsers();

        assertEquals(List.of("root", "child"), result.stream().map(UserDto::getUsername).toList());
    }

    @Test
    void findUserById_whenVisible_returnsUser() {
        when(userRepository.getRequiredById(2L)).thenReturn(child);

        UserDto result = service.findUserById(2L);

        verify(accessPolicy).checkCanView(root, child);
        assertEquals("child", result.getUsername());
        assertEquals("root", result.getCreatedBy());
    }

    @Test
    void findUserById_whenNotVisible_reportsNotFound() {
        when(userRepository.getRequiredById(2L)).thenReturn(child);
        doThrow(new DataNotFoundException("User not found")).when(accessPolicy).checkCanView(root, child);

        assertThrows(DataNotFoundException.class, () -> service.findUserById(2L));
    }

    @Test
    void findUserByUsername_returnsVisibleUser() {
        when(userRepository.getRequiredByUsername("child")).thenReturn(child);

        assertEquals("child", service.findUserByUsername("child").getUsername());
        verify(accessPolicy).checkCanView(root, child);
    }

    @Test
    void findUserByEmail_returnsVisibleUser() {
        when(userRepository.getRequiredByEmail("child@example.com")).thenReturn(child);

        assertEquals("child@example.com", service.findUserByEmail("child@example.com").getEmail());
        verify(accessPolicy).checkCanView(root, child);
    }

    @Test
    void deleteUser_whenAllowed_deletesUser() {
        when(userRepository.getRequiredById(2L)).thenReturn(child);

        service.deleteUser(2L);

        verify(accessPolicy).checkCanDelete(root, child);
        verify(userRepository).delete(child);
    }

    @Test
    void deleteUser_whenNotAllowed_keepsUser() {
        when(userRepository.getRequiredById(2L)).thenReturn(child);
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanDelete(root, child);

        assertThrows(NoAccessException.class, () -> service.deleteUser(2L));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_whenUserNotFound_isRejected() {
        when(userRepository.getRequiredById(2L)).thenThrow(new DataNotFoundException("User not found"));

        assertThrows(DataNotFoundException.class, () -> service.deleteUser(2L));
        verify(userRepository, never()).delete(any());
    }
}
