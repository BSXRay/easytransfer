package bsxray.easytransfer.command;

import static bsxray.easytransfer.util.Constants.PERMISSION_ADMIN;

import bsxray.easytransfer.config.ConfigManager;
import bsxray.easytransfer.service.TransferService;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The main plugin command.
 * <p>
 * Syntax:
 * <ul>
 *   <li>{@code /<alias> transfer <name>} – execute a transfer</li>
 *   <li>{@code /<alias> reload}          – reload the config</li>
 * </ul>
 *
 * Tab completion is provided for sub-commands and transfer names.
 */
public class EasyTransferCommand implements RawCommand {

    private final ConfigManager    config;
    private final TransferService  transferService;
    private final Runnable         reloadAction;

    public EasyTransferCommand(final ConfigManager config,
                               final TransferService transferService,
                               final Runnable reloadAction) {
        this.config           = config;
        this.transferService  = transferService;
        this.reloadAction     = reloadAction;
    }

    /* ───── permission ───── */

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION_ADMIN);
    }

    /* ───── execution ───── */

    @Override
    public void execute(final Invocation invocation) {
        final CommandSource source = invocation.source();
        final String[] args = invocation.arguments().split(" ", -1);

        if (args.length == 0 || args[0].isEmpty()) {
            usage(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "transfer" -> handleTransfer(source, args);
            case "reload"   -> handleReload(source);
            default         -> usage(source);
        }
    }

    /* ───── tab completion ───── */

    @Override
    public List<String> suggest(final Invocation invocation) {
        final String raw   = invocation.arguments();
        final String[] args = raw.split(" ", -1);
        final boolean hasSpace = raw.contains(" ");

        // First word
        if (!hasSpace) {
            final String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            final List<String> result = new ArrayList<>(2);
            if ("transfer".startsWith(prefix)) result.add("transfer");
            if ("reload".startsWith(prefix))   result.add("reload");
            return result;
        }

        // Second word after "transfer"
        if (args.length == 2 && "transfer".equalsIgnoreCase(args[0])) {
            final String prefix = args[1].toLowerCase();
            return config.transferNames().stream()
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .toList();
        }

        return Collections.emptyList();
    }

    /* ───── sub-handlers ───── */

    private void handleTransfer(final CommandSource source, final String[] args) {
        if (args.length < 2 || args[1].isEmpty()) {
            source.sendMessage(Component.text(
                    "Usage: /%s transfer <name>".formatted(config.alias()),
                    NamedTextColor.RED));
            source.sendMessage(Component.text(
                    "Available: " + String.join(", ", config.transferNames()),
                    NamedTextColor.GRAY));
            return;
        }

        final String name = args[1];
        if (!config.transferNames().contains(name)) {
            source.sendMessage(Component.text(
                    "Transfer '" + name + "' not found", NamedTextColor.RED));
            source.sendMessage(Component.text(
                    "Available: " + String.join(", ", config.transferNames()),
                    NamedTextColor.GRAY));
            return;
        }

        transferService.startTransfer(name, source);
    }

    private void handleReload(final CommandSource source) {
        reloadAction.run();
        source.sendMessage(Component.text("EasyTransfer config reloaded", NamedTextColor.GREEN));
    }

    /* ───── usage ───── */

    private void usage(final CommandSource source) {
        final String alias = config.alias();
        source.sendMessage(Component.text("EasyTransfer v1.0", NamedTextColor.GOLD));
        source.sendMessage(Component.text("/" + alias + " transfer <name>", NamedTextColor.YELLOW)
                .append(Component.text(" — Transfer a server between nodes", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/" + alias + " reload", NamedTextColor.YELLOW)
                .append(Component.text(" — Reload configuration", NamedTextColor.GRAY)));
    }
}
