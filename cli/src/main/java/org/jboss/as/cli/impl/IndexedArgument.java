package org.jboss.as.cli.impl;

import org.jboss.as.cli.ArgumentValueConverter;
import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.operation.ParsedCommandLine;

import java.util.List;
import java.util.Set;

public class IndexedArgument extends ArgumentWithValue {
    public IndexedArgument(CommandHandlerWithArguments handler, String fullName) {
        super(handler, fullName);
    }

    public IndexedArgument(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter, String fullName) {
        super(handler, valueCompleter, fullName);
    }

    public IndexedArgument(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter, ArgumentValueConverter valueConverter, String fullName) {
        super(handler, valueCompleter, valueConverter, fullName);
    }

    public IndexedArgument(CommandHandlerWithArguments handler, int index, String fullName) {
        super(handler, index, fullName);
    }

    public IndexedArgument(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter, int index, String fullName) {
        super(handler, valueCompleter, index, fullName);
    }

    public IndexedArgument(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter, ArgumentValueConverter valueConverter, String fullName, String shortName) {
        super(handler, valueCompleter, valueConverter, fullName, shortName);
    }

    @Override
    public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {

        if(!access.isSatisfied(ctx)) {
            return false;
        }

        ParsedCommandLine args = ctx.getParsedCommandLine();
        if (exclusive) {
            final Set<String> propertyNames = args.getPropertyNames();
            if(propertyNames.isEmpty()) {
                final List<String> values = args.getOtherProperties();
                if(values.isEmpty()) {
                    return true;
                }
                if(index == -1) {
                    return false;
                }
                if(index == 0 && values.size() == 1) {
                    return !isValueComplete(args);
                } else {
                    return true;
                }
            }

            if(propertyNames.size() != 1) {
                return false;
            }

            if(args.getLastParsedPropertyName() == null) {
                return false;
            }

            final List<String> values = args.getOtherProperties();
            if(!values.isEmpty()) {
                return false;
            }

            // The argument is already there, don't add it.
            if (fullName.equals(args.getLastParsedPropertyName())) {
                return false;
            }

            return fullName.startsWith(args.getLastParsedPropertyName()) || (shortName != null && shortName.startsWith(args.getLastParsedPropertyName()));
        }

        if (isPresent(args) && isValueComplete(args)) {
            return false;
        }

        for (CommandArgument arg : cantAppearAfter) {
            if (arg.isPresent(args)) {
                return false;
            }
        }

        if (requiredPreceding != null) {
            for (CommandArgument arg : requiredPreceding) {
                if (arg.isPresent(args)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }
}
