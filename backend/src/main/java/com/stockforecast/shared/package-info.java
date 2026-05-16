/**
 * Shared kernel: small, stable, cross-cutting building blocks (error model, identifiers, web plumbing, time)
 * used by every module. Business concepts must live in their owning module, never here.
 */
@ApplicationModule(displayName = "Shared Kernel", type = ApplicationModule.Type.OPEN)
package com.stockforecast.shared;

import org.springframework.modulith.ApplicationModule;
