package xyz.lawlietcache.booru.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Service refused")
public class ServiceRefusedException extends RuntimeException {
}
