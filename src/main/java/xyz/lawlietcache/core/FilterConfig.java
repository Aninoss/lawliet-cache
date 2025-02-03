package xyz.lawlietcache.core;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<StaticTokenFilter> staticTokenFilterRegistration() {
        FilterRegistrationBean<StaticTokenFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new StaticTokenFilter());
        registrationBean.addUrlPatterns("/api/*");
        return registrationBean;
    }

}
