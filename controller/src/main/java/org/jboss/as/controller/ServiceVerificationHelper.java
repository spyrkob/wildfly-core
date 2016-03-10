/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartException;

/**
 * Tracks the status of a service installed by an {@link OperationStepHandler}, recording a failure desription
 * if the service has a problems starting.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a> *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
@SuppressWarnings("deprecation")
class ServiceVerificationHelper extends AbstractServiceListener<Object> implements ServiceListener<Object>, OperationStepHandler {
    private final StabilityMonitor monitor = new StabilityMonitor();

    @Override
    public void listenerAdded(ServiceController<?> controller) {
        monitor.addController(controller);
        controller.removeListener(this);
    }

    StabilityMonitor getMonitor() {
        return monitor;
    }

    public synchronized void execute(final OperationContext context, final ModelNode operation) {
        final Set<ServiceController<?>> failed = new HashSet<ServiceController<?>>();
        final Set<ServiceController<?>> problems = new HashSet<ServiceController<?>>();

        try {
            monitor.awaitStability(failed, problems);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.operationCancelled());
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            return;
        } finally {
            monitor.clear();
        }

        if (!failed.isEmpty() || !problems.isEmpty()) {
            // generate list of unavailable services
            final Set<ServiceName> unavailableServices = findUnavailableServices(failed, problems);
            // generate list of services that don't have any directly unavailable services
            final Set<ServiceController<?>> missingTransitive = findMisingTransitiveServices(problems);
            // generate lists of services with direct unavailable services dependency
            final List<String> problemList = findUnavailableServicesDependants(problems);
            // generate lists of all missing services across the container
            final SortedSet<ServiceName> allMissing = findAllMissingServices(missingTransitive);


            // prepare failure report
            final ModelNode failureDescription = context.getFailureDescription();
            reportFailedServices(failed, failureDescription);
            reportUnavailableRequiredServices(unavailableServices, failureDescription);
            reportImmediateDependants(problemList, failureDescription);
            // See if any other services are known to the service container as being missing and aren't already
            // tracked by this SVH as failed or directly missing. If any are found, that is some additional
            // info to the user, so report that
            if (containsUntrackedServices(allMissing, unavailableServices)) {
                reportAllMissingServices(allMissing, missingTransitive, failureDescription);
            }

            if (context.isRollbackOnRuntimeFailure()) {
                context.setRollbackOnly();
            }
        }
    }

    private static Set<ServiceName> findUnavailableServices(Set<ServiceController<?>> failed, Set<ServiceController<?>> problems) {
        Set<ServiceName> unavailableServices = new HashSet<>();
        for (ServiceController<?> controller : failed) {
            unavailableServices.add(controller.getName());
        }
        for (ServiceController<?> controller : problems) {
            final Set<ServiceName> dependencies = controller.getImmediateUnavailableDependencies();
            for (ServiceName dependency : dependencies) {
                unavailableServices.add(dependency);
            }
        }
        return unavailableServices;
    }

    private static Set<ServiceController<?>> findMisingTransitiveServices(Set<ServiceController<?>> problems) {
        Set<ServiceController<?>> missingTransitive = new HashSet<>();
        for (ServiceController<?> controller : problems) {
            if (controller.getImmediateUnavailableDependencies().isEmpty()) {
                missingTransitive.add(controller);
            }
        }
        return missingTransitive;
    }

    private static List<String> findUnavailableServicesDependants(Set<ServiceController<?>> problems) {
        List<String> problemList = new ArrayList<>();
        for (ServiceController<?> controller : problems) {
            Set<ServiceName> immediatelyUnavailable = controller.getImmediateUnavailableDependencies();
            if (!immediatelyUnavailable.isEmpty()) {
                StringBuilder missing = new StringBuilder();
                for (Iterator<ServiceName> i = immediatelyUnavailable.iterator(); i.hasNext(); ) {
                    ServiceName missingSvc = i.next();
                    missing.append(missingSvc.getCanonicalName());
                    if (i.hasNext()) {
                        missing.append(", ");
                    }
                }

                final StringBuilder problem = new StringBuilder();
                problem.append(controller.getName().getCanonicalName());
                problem.append(" ").append(ControllerLogger.ROOT_LOGGER.servicesMissing(missing));
                problemList.add(problem.toString());
            }
        }
        return problemList;
    }

    private static void reportFailedServices(Set<ServiceController<?>> failed, ModelNode failureDescription) {
        if (!failed.isEmpty()) {
            ModelNode failedList = failureDescription.get(ControllerLogger.ROOT_LOGGER.failedServices());
            for (ServiceController<?> controller : failed) {
                ServiceName serviceName = controller.getName();
                failedList.get(serviceName.getCanonicalName()).set(getServiceFailureDescription(controller.getStartException()));
            }
        }
    }

    private static void reportUnavailableRequiredServices(Set<ServiceName> unavailableServices, ModelNode failureDescription) {
        if (!unavailableServices.isEmpty()) {
            ModelNode requiredServicesNode = failureDescription.get(ControllerLogger.ROOT_LOGGER.missingRequiredServices());
            for (ServiceName serviceName : unavailableServices) {
                requiredServicesNode.add(serviceName.getCanonicalName());
            }
        }
    }

    private static void reportImmediateDependants(List<String> problemList, ModelNode failureDescription) {
        if (!problemList.isEmpty()) {
            ModelNode problemListNode = failureDescription.get(ControllerLogger.ROOT_LOGGER.servicesMissingDependencies());
            for (String problem : problemList) {
                problemListNode.add(problem);
            }
        }
    }

    private static void reportAllMissingServices(SortedSet<ServiceName> allMissing, Set<ServiceController<?>> missingTransitive, ModelNode failureDescription) {
        ModelNode missingTransitiveDesc = failureDescription.get(ControllerLogger.ROOT_LOGGER.missingTransitiveDependencyProblem());
        ModelNode missingTransitiveDeps = missingTransitiveDesc.get(ControllerLogger.ROOT_LOGGER.missingTransitiveDependents());
        Set<ServiceName> sortedNames = new TreeSet<>();
        for (ServiceController<?> serviceController : missingTransitive) {
            sortedNames.add(serviceController.getName());
        }
        for (ServiceName serviceName : sortedNames) {
            missingTransitiveDeps.add(serviceName.getCanonicalName());
        }
        ModelNode allMissingList = missingTransitiveDesc.get(ControllerLogger.ROOT_LOGGER.missingTransitiveDependencies());
        for (ServiceName serviceName : allMissing) {
            allMissingList.add(serviceName.getCanonicalName());
        }
    }

    private static ModelNode getServiceFailureDescription(final StartException exception) {
        final ModelNode result = new ModelNode();
        if (exception != null) {
            StringBuilder sb = new StringBuilder(exception.toString());
            Throwable cause = exception.getCause();
            while (cause != null) {
                sb.append("\n    Caused by: ");
                sb.append(cause.toString());
                cause = cause.getCause();
            }
            result.set(sb.toString());
        }
        return result;
    }

    private static SortedSet<ServiceName> findAllMissingServices(Set<ServiceController<?>> missingTransitive) {
        // Check all relevant service containers. This is a bit silly since in reality there
        // should only be one that is associated with every SC that is passed in,
        // but I'm being anal and vaguely future-proofing a bit

        Set<ServiceContainer> examined = new HashSet<ServiceContainer>();
        SortedSet<ServiceName> allMissingServices = new TreeSet<ServiceName>();
        for (ServiceController<?> controller : missingTransitive) {
            ServiceContainer container = controller.getServiceContainer();
            if (examined.add(container)) {
                allMissingServices.addAll(findAllMissingServices(container));
            }
        }

        return allMissingServices;
    }

    private static boolean containsUntrackedServices(SortedSet<ServiceName> result, Set<ServiceName> alreadyTracked) {
        final SortedSet<ServiceName> allMissingServices = result;
        Set<ServiceName> retain = new HashSet<>(allMissingServices);
        retain.removeAll(alreadyTracked);

        return retain.size() != 0;
    }

    private static Set<ServiceName> findAllMissingServices(ServiceContainer container) {
        Set<ServiceName> result = new HashSet<ServiceName>();
        for (ServiceName serviceName : container.getServiceNames()) {
            ServiceController<?> controller = container.getService(serviceName);
            if (controller != null && controller.getMode() != ServiceController.Mode.NEVER && controller.getMode() != ServiceController.Mode.REMOVE
                    && controller.getSubstate() == ServiceController.Substate.PROBLEM) {
                result.addAll(controller.getImmediateUnavailableDependencies());
            }
        }
        return result;
    }

    static ModelNode extractFailedServicesDescription(ModelNode failureDescription) {
        return extractIfPresent(ControllerLogger.ROOT_LOGGER.failedServices(), failureDescription);
    }

    static ModelNode extractMissingServicesDescription(ModelNode failureDescription) {
        return extractIfPresent(ControllerLogger.ROOT_LOGGER.servicesMissingDependencies(), failureDescription);
    }

    static ModelNode extractTransitiveDependencyProblemDescription(ModelNode failureDescription) {
        return extractIfPresent(ControllerLogger.ROOT_LOGGER.missingTransitiveDependencyProblem(), failureDescription);
    }

    private static ModelNode extractIfPresent(String key, ModelNode modelNode) {
        ModelNode result = null;
        if (modelNode.hasDefined(key)) {
            result = modelNode.get(key);
        }
        return result;

    }
}

