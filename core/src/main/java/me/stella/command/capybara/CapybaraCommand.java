package me.stella.command.capybara;

import me.stella.command.DispatchingCommand;
import me.stella.command.annotations.CommandMeta;

/** {@code /capybara} - dispatches to the sub-commands in this package. */
@CommandMeta(name = "capybara", description = "Capybara item scanner and duplication-detection tools", usage = "/capybara <sub-command>",
        permission = "capybara.admin")
public final class CapybaraCommand extends DispatchingCommand {
}
