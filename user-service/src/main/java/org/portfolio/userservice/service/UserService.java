package org.portfolio.userservice.service;

import org.portfolio.userservice.dto.*;
import org.portfolio.userservice.entity.User;
import org.portfolio.userservice.exception.UserNotFoundException;
import org.portfolio.userservice.repository.UserRepository;
import org.portfolio.userservice.security.jwt.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.security.authentication.BadCredentialsException;
import java.util.Optional;


@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public JwtAuthenticationDto signIn(UserCredentialsDto userCredentialsDto) {
        User user=findUserByCredential(userCredentialsDto);
        return jwtService.generateAuthToken(user.getId(),user.getEmail());
    }

    public JwtAuthenticationDto refreshToken(RefreshTokenDto refreshTokenDto) {
        String refreshToken=refreshTokenDto.getRefreshToken();
        if(refreshToken!=null && jwtService.validateRefreshToken(refreshToken)){
            User user = userRepository.findById(jwtService.getUserIdFromToken(refreshToken))
                    .filter(candidate -> !candidate.isDeleting())
                    .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
            return jwtService.refreshBaseToken(refreshToken, user.getId(), user.getEmail());
        }
        throw new BadCredentialsException("Invalid refresh token");
    }

    public UserDto createUser(RegisterRequestDto input) {
        final User createdUser = User.builder()
                .name(input.getName())
                .surname(input.getSurname())
                .email(input.getEmail())
                .password(passwordEncoder.encode(input.getPassword()))
                .build();
        final User saved = userRepository.save(createdUser);
        return toDto(saved);
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .surname(user.getSurname())
                .email(user.getEmail())
                .build();
    }

    public UserDto getUserById(Long id) {
        return userRepository.findById(id)
                .filter(user -> !user.isDeleting())
                .map(this::toDto)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateUser(Long id, UserDto userDto) {
        User user = userRepository.findForUpdate(id).filter(candidate -> !candidate.isDeleting())
                .orElseThrow(() -> new UserNotFoundException(id));

        user.setName(userDto.getName());
        user.setSurname(userDto.getSurname());
        user.setEmail(userDto.getEmail());

        userRepository.save(user);
    }

    public User findUserByCredential(UserCredentialsDto userCredentialsDto) {
        Optional<User> optionalUser =userRepository.findByEmail(userCredentialsDto.getEmail());
        if(optionalUser.isPresent()){
            User user=optionalUser.get();
            if(!user.isDeleting() && passwordEncoder.matches(userCredentialsDto.getPassword(),user.getPassword())) {
                return user;
            }
        }
        throw new BadCredentialsException("Email or password is not correct");
    }
}
