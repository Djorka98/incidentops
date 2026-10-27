package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.UserRequest;
import com.djorka.incidentops.dto.UserResponse;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.exception.ResourceConflictException;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.UserRepository;
import com.djorka.incidentops.repository.CommentRepository;
import com.djorka.incidentops.repository.IncidentHistoryRepository;
import com.djorka.incidentops.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private IncidentHistoryRepository incidentHistoryRepository;
    @InjectMocks
    private UserService userService;

    @Test
    void getsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "Admin", "admin@example.com", UserRole.ADMIN),
                user(2L, "Viewer", "viewer@example.com", UserRole.VIEWER)));

        List<UserResponse> users = userService.getAllUsers();

        assertThat(users).extracting(UserResponse::email)
                .containsExactly("admin@example.com", "viewer@example.com");
        assertThat(users).extracting(UserResponse::role)
                .containsExactly(UserRole.ADMIN, UserRole.VIEWER);
    }

    @Test
    void createsUserFromRequest() {
        UserRequest request = new UserRequest("Responder", "responder@example.com", UserRole.RESPONDER);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        UserResponse response = userService.createUser(request);

        assertThat(response).isEqualTo(
                new UserResponse(10L, "Responder", "responder@example.com", UserRole.RESPONDER));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNull();
    }

    @Test
    void patchesExistingUser() {
        User existing = user(1L, "Old", "old@example.com", UserRole.VIEWER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponse response = userService.patchUser(
                1L, new UserRequest("New", "new@example.com", UserRole.ADMIN));

        assertThat(response).isEqualTo(new UserResponse(1L, "New", "new@example.com", UserRole.ADMIN));
    }

    @Test
    void rejectsDuplicateEmailWhenCreatingUser() {
        UserRequest request = new UserRequest("Duplicate", "used@example.com", UserRole.VIEWER);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsEmailOwnedByAnotherUserWhenPatching() {
        User existing = user(1L, "Existing", "existing@example.com", UserRole.VIEWER);
        User owner = user(2L, "Owner", "used@example.com", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("used@example.com")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> userService.patchUser(
                1L, new UserRequest("Existing", "used@example.com", UserRole.VIEWER)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsPatchForUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.patchUser(
                404L, new UserRequest("Name", "name@example.com", UserRole.VIEWER)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id:404");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deletesUnreferencedUser() {
        User target = user(2L, "Viewer", "viewer@example.com", UserRole.VIEWER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        userService.deleteUser(2L, 1L);

        verify(incidentRepository).existsByAssignedToId(2L);
        verify(commentRepository).existsByAuthorId(2L);
        verify(incidentHistoryRepository).existsByPerformedById(2L);
        verify(userRepository).delete(target);
        verify(userRepository).flush();
    }

    @Test
    void rejectsDeleteForUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(404L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id:404");

        verify(userRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingCurrentUser() {
        User current = user(1L, "Admin", "admin@example.com", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> userService.deleteUser(1L, 1L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Current user cannot be deleted");

        verify(incidentRepository, never()).existsByAssignedToId(any());
        verify(userRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingAssignedUser() {
        assertReferencedUserCannotBeDeleted();
        when(incidentRepository.existsByAssignedToId(2L)).thenReturn(true);

        assertOperationalReferenceConflict();
    }

    @Test
    void rejectsDeletingCommentAuthor() {
        assertReferencedUserCannotBeDeleted();
        when(commentRepository.existsByAuthorId(2L)).thenReturn(true);

        assertOperationalReferenceConflict();
    }

    @Test
    void rejectsDeletingHistoryActor() {
        assertReferencedUserCannotBeDeleted();
        when(incidentHistoryRepository.existsByPerformedById(2L)).thenReturn(true);

        assertOperationalReferenceConflict();
    }

    @Test
    void translatesLateForeignKeyConflict() {
        User target = user(2L, "Viewer", "viewer@example.com", UserRole.VIEWER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        doThrow(new DataIntegrityViolationException("foreign key detail"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> userService.deleteUser(2L, 1L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("User cannot be deleted because it is referenced by operational data");

        verify(userRepository).delete(target);
    }

    private void assertReferencedUserCannotBeDeleted() {
        User target = user(2L, "Operator", "operator@example.com", UserRole.RESPONDER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
    }

    private void assertOperationalReferenceConflict() {
        assertThatThrownBy(() -> userService.deleteUser(2L, 1L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("User cannot be deleted because it is referenced by operational data");

        verify(userRepository, never()).delete(any());
    }

    private User user(Long id, String name, String email, UserRole role) {
        return User.builder().id(id).name(name).email(email).role(role).build();
    }
}
