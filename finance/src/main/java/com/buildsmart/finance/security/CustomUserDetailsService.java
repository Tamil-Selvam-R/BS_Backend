package com.buildsmart.finance.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // UserRepository removed: users table is no longer present

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        throw new UsernameNotFoundException("User not found with email: " + email);
    }

}
