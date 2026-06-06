package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.identity.AuthenticatedUser;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * The authenticated principal is resolved from the access token, not supplied by clients, so it must not
 * appear as a request parameter in the API documentation.
 */
@Configuration(proxyBeanMethods = false)
class IdentityOpenApiConfiguration {

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthenticatedUser.class);
    }
}
