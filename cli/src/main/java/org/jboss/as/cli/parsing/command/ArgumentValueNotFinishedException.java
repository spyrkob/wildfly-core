package org.jboss.as.cli.parsing.command;

import org.jboss.as.cli.CommandFormatException;

public class ArgumentValueNotFinishedException extends CommandFormatException {
    public ArgumentValueNotFinishedException(String message) {
        super(message);
    }
}
