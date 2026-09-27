package org.portfolio.userservice.service;

import org.portfolio.userservice.dto.*;
import org.portfolio.userservice.entity.User;
import org.portfolio.userservice.exception.UserNotFoundException;
import org.portfolio.userservice.repository.UserRepository;
import org.portfolio.userservice.security.SecurityConfig;
import org.portfolio.userservice.security.jwt.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.naming.AuthenticationException;
import java.util.Optional;


@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, SecurityConfig securityConfig, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public JwtAuthenticationDto signIn(UserCredentialsDto userCredentialsDto) throws AuthenticationException {
        User user=findUserByCredential(userCredentialsDto);
        return jwtService.generateAuthToken(user.getEmail());
    }

    public JwtAuthenticationDto refreshToken(RefreshTokenDto refreshTokenDto) throws Exception {
        String refreshToken=refreshTokenDto.getRefreshToken();
        if(refreshToken!=null && jwtService.validateJwtToken(refreshToken)){
            User user=findByEmail(jwtService.getEmailFromToken(refreshToken));
            return jwtService.refreshBaseToken(user.getEmail(), refreshToken);
        }
        throw new AuthenticationException("Invalid refresh token");
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
                .map(this::toDto)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public void updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        user.setName(userDto.getName());
        user.setSurname(userDto.getSurname());
        user.setEmail(userDto.getEmail());

        userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.delete(user);
    }

    public User findUserByCredential(UserCredentialsDto userCredentialsDto) throws AuthenticationException {
        Optional<User> optionalUser =userRepository.findByEmail(userCredentialsDto.getEmail());
        if(optionalUser.isPresent()){
            User user=optionalUser.get();
            if(passwordEncoder.matches(userCredentialsDto.getPassword(),user.getPassword())) { //comparing writing password with db password
                return user;
            }
        }
        throw new AuthenticationException("Email or password is not correct");
    }
    private User findByEmail(String email) throws Exception{
        return userRepository.findByEmail(email).orElseThrow(()->new Exception(String.format("User with email %s not found", email)));
    }
}
