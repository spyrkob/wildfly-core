/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.version.Stability;

/**
 * Implemented by objects that register features.
 * @author Paul Ferraro
 */
public interface FeatureRegistry {
    /**
     * Returns the feature stability supported by this feature registry.
     * @return a stability level
     */
    default Stability getStability() {
        // TODO Default implementation is only here to prevent wildfly-full integration test failures
        // Remove this once that is no longer the case
        return Stability.DEFAULT;
    }

    /**
     * Determines whether the specified feature is enabled by the configured stability level of the feature registry.
     * @param <F> the feature type
     * @param feature a feature
     * @return true, if the specified feature is enabled, false otherwise.
     */
    default <F extends Feature> boolean enables(F feature) {
        return this.getStability().enables(feature.getStability());
    }
}
