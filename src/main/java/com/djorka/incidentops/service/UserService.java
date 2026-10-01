package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.UserRequest;
import com.djorka.incidentops.dto.UserResponse;
import com.djorka.incidentops.exception.ResourceConflictException;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole());
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException("Email is already registered");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .role(request.role())
                .build();

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse patchUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id:" + id));

        userRepository.findByEmail(request.email())
                .filter(existingUser -> !existingUser.getId().equals(id))
                .ifPresent(existingUser -> {
                    throw new ResourceConflictException("Email is already registered");
                });

        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }
}
