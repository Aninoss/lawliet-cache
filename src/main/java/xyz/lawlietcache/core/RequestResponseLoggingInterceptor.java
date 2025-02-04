package xyz.lawlietcache.core;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestResponseLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingInterceptor.class);

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex != null) {
            String originalRequestURI = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            if (originalRequestURI == null) {
                originalRequestURI = request.getRequestURI();
            }
            logger.error("Request to {} resulted in exception: {}", originalRequestURI, ex.getMessage());
        }
    }

}
