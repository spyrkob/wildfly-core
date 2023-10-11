package org.wildfly.core.instmgr.cli;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrVersionsHandler;

import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

@CommandDefinition(name = "versions", description = "List latest installed manifest versions.", activator = InstMgrActivator.class)
public class VersionsCommand extends AbstractInstMgrCommand {

    @Override
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        op.get(OP).set(InstMgrVersionsHandler.DEFINITION.getName());

        return OperationBuilder.create(op).build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        ModelNode response = this.executeOp(ctx, this.host);
        ModelNode result = response.get(RESULT);

        final List<ModelNode> list = result.get("installed-versions").asList();

        ctx.printLine("Installed versions:");
        for (ModelNode name : list) {
            ctx.printLine(name.get("name").asString());
        }

        return CommandResult.SUCCESS;
    }
}
