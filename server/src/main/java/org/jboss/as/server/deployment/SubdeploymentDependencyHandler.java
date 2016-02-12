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

package org.jboss.as.server.deployment;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.management.ServiceStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SubdeploymentDependencyHandler implements OperationStepHandler {

    private final ServiceName deploymentUnitServiceName;

    public SubdeploymentDependencyHandler(ServiceName deploymentUnitServiceName) {
        this.deploymentUnitServiceName = deploymentUnitServiceName;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ServiceController<?> controller = context.getServiceRegistry(false).getService(deploymentUnitServiceName);
        final Set<ServiceController<?>> problems = ((AbstractDeploymentUnitService) controller.getService()).getProblems();

        detectInterDeploymentDependency(problems);
    }

    private void detectInterDeploymentDependency(Set<ServiceController<?>> failedServices) {
        Map<String, ServiceStatus> serviceMap = null;

        List<String[]> interDeploymentDependencies = new ArrayList<>();
        for (ServiceController<?> service : failedServices) {
            if (serviceMap == null) {
                serviceMap = new HashMap<>();
                for (ServiceStatus serviceStatus : service.getServiceContainer().queryServiceStatuses()) {
                    serviceMap.put(serviceStatus.getServiceName(), serviceStatus);
                }
            }

            final String failedServiceName = service.getName().getCanonicalName();
            if (isInstallPhaseSubdeploymentService(failedServiceName)) {
                for (String dependencyName : serviceMap.get(failedServiceName).getDependencies()) {
                    final String dependencyState = getDependencyState(serviceMap, dependencyName);

                    if (isFailedDeploymentCompleteService(dependencyName, dependencyState)) {
                        System.out.println("      * " + dependencyName + " (" + dependencyState + ")");

                        final String dependingDeployment = deploymentName(failedServiceName, ".INSTALL");
                        final String dependencyDeployment = deploymentName(dependencyName, ".deploymentCompleteService");

                        interDeploymentDependencies.add(new String[]{deploymentDescription(dependingDeployment, service.getState().name()), deploymentDescription(dependencyDeployment, dependencyState)});
                    }
                }
            }
        }
        for (String[] interDeploymentDependency : interDeploymentDependencies) {
            final String msg = String.format("Deployment of [%s] failed due to unsatisfied dependency on [%s]", interDeploymentDependency[0], interDeploymentDependency[1]);
            ServerLogger.DEPLOYMENT_LOGGER.log(Logger.Level.ERROR, msg);
        }
    }

    private String deploymentDescription(String dependingDeployment, String state) {

        return dependingDeployment + " (" + state + ")";
    }

    private String deploymentName(String failedServiceName, String suffix) {
        int endIndex = failedServiceName.indexOf(suffix);
        return failedServiceName.substring("jboss.deployment.subunit".length() + 1, endIndex);
    }

    private boolean isFailedDeploymentCompleteService(String dependenciesName, String dependencyState) {
        return !ServiceController.State.UP.name().equals(dependencyState) && dependenciesName.endsWith("deploymentCompleteService");
    }

    private String getDependencyState(Map<String, ServiceStatus> serviceMap, String dependenciesName) {
        final ServiceStatus dependencyServiceStatus = serviceMap.get(dependenciesName);
        return (dependencyServiceStatus == null) ? "MISSING" : dependencyServiceStatus.getStateName();
    }

    private boolean isInstallPhaseSubdeploymentService(String failedServiceName) {
        return failedServiceName.startsWith(Services.JBOSS_DEPLOYMENT_SUB_UNIT.getCanonicalName()) && failedServiceName.endsWith("INSTALL");
    }
}
