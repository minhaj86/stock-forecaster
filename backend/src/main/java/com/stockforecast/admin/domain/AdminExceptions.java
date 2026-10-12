package com.stockforecast.admin.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

/**
 * Business failures specific to the admin console.
 */
public final class AdminExceptions {

    private AdminExceptions() {
    }

    /** Provider metadata cannot be used to register the stock; the administrator can enter it manually. */
    public static class ProviderMetadataIncompleteException extends DomainException {
        public ProviderMetadataIncompleteException(String reason) {
            super("PROVIDER_METADATA_UNAVAILABLE", ErrorCategory.UNPROCESSABLE,
                    "The provider's metadata cannot be used (%s); enter the details manually".formatted(reason));
        }
    }
}
