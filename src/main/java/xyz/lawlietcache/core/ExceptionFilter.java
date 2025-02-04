package xyz.lawlietcache.core;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class ExceptionFilter extends Filter<ILoggingEvent> {

    public ExceptionFilter() {
    }

    @Override
    public FilterReply decide(final ILoggingEvent event) {
        final IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy == null) {
            return FilterReply.NEUTRAL;
        }

        if (!(throwableProxy instanceof ThrowableProxy throwableProxyImpl)) {
            return FilterReply.NEUTRAL;
        }

        String message = throwableProxyImpl.getThrowable().toString();
        if (message.contains("The current thread was interrupted")) {
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }

}
