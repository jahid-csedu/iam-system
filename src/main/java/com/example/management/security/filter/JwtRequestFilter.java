package com.example.management.security.filter;

import com.example.management.constant.ErrorMessage;
import com.example.management.constant.JwtConstant;
import com.example.management.exception.JwtException;
import com.example.management.security.jwt.JwtTokenUtil;
import com.example.management.security.user.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenUtil tokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authorizationHeader = request.getHeader(JwtConstant.REQUEST_HEADER);
        String username = null;
        String jwtToken = null;
        if(authorizationHeader != null && authorizationHeader.startsWith(JwtConstant.BEARER)) {
            jwtToken = authorizationHeader.replace(JwtConstant.BEARER, "");
            try {
                username = tokenUtil.getUsernameFromToken(jwtToken);
            } catch (Exception e) {
                throw new JwtException(ErrorMessage.INVALID_TOKEN);
            }
        }else {
            log.error("Token doesn't start with Bearer ");
        }

        if(username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
            if(tokenUtil.validateToken(jwtToken, userDetails)) {
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return request.getServletPath().equals("/user/authenticate") ||
                request.getServletPath().equals("/user/token/refresh");
    }
}
