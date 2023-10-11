package org.wildfly.core.instmgr;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.ManifestVersion;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.nio.file.Path;
import java.util.Collection;

public class InstMgrVersionsHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "current-versions";

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY).setReplyType(ModelType.LIST).setRuntimeOnly().setReplyValueType(ModelType.OBJECT).build();


    InstMgrVersionsHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        context.addStep((context1, operation1) -> {
            Path serverHome = imService.getHomeDir();
            MavenOptions mavenOptions = new MavenOptions(null, false);
            try {
                final InstallationManager installationManager = imf.create(serverHome, mavenOptions);
                final Collection<ManifestVersion> installedVersions = installationManager.getInstalledVersions();

                final ModelNode resulList = new ModelNode();

                for (ManifestVersion installedVersion : installedVersions) {
                    ModelNode versionNode = new ModelNode();
                    if (installedVersion.getDescription() != null) {
                        versionNode.get("name").set(installedVersion.getDescription());
                    } else {
                        versionNode.get("name").set(installedVersion.getChannelId() + ":" + installedVersion.getVersion());
                    }
                    resulList.get("installed-versions").add(versionNode);
                }

                context.getResult().set(resulList);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
