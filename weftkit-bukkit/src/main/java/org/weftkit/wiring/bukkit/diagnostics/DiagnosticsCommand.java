package org.weftkit.wiring.bukkit.diagnostics;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.weftkit.wiring.runtime.WeftDiagnostics;
import org.weftkit.wiring.runtime.WeftDiagnostics.Component;
import org.weftkit.wiring.runtime.WeftDiagnostics.Snapshot;

/**
 * Opt-in Bukkit executor requiring {@code weftkit.diagnostics}. Bind a wired subclass with
 * {@code @CommandHandler} and a plugin.yml entry, or register manually. Lists accept a class-name
 * filter and show at most 50 rows. Failure messages are omitted.
 */
public class DiagnosticsCommand implements CommandExecutor {
    public static final String PERMISSION = "weftkit.diagnostics";

    private static final int MAX_ROWS = 50;

    private enum Section {
        SUMMARY(component -> false),
        COMPONENTS(component -> true),
        MODULES(Component::module),
        FLAGS(component -> component.flag().isPresent()),
        FAILURES(component -> component.failure().isPresent()),
        TIMINGS(component -> component.loadTime().isPresent());

        private final Predicate<Component> includes;

        Section(Predicate<Component> includes) {
            this.includes = includes;
        }

        static Section parse(String name) {
            return Arrays.stream(values())
                    .filter(section -> section.name().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        }

        static String choices() {
            return Arrays.stream(values())
                    .map(section -> section.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining("|"));
        }
    }

    private final WeftDiagnostics diagnostics;

    public DiagnosticsCommand(WeftDiagnostics diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("You do not have permission to view diagnostics.");
            return true;
        }
        Section section = args.length == 0 ? Section.SUMMARY : Section.parse(args[0]);
        if (args.length > 2 || section == null) {
            sender.sendMessage("Usage: /" + label + " [" + Section.choices() + "] [filter]");
            return true;
        }
        Snapshot snapshot = diagnostics.snapshot();
        sender.sendMessage(summary(snapshot));
        if (section != Section.SUMMARY) {
            String filter = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
            List<Component> matches = snapshot.components().stream()
                    .filter(section.includes)
                    .filter(component ->
                            component.type().getName().toLowerCase(Locale.ROOT).contains(filter))
                    .toList();
            sendComponents(sender, matches);
        }
        return true;
    }

    private static String summary(Snapshot snapshot) {
        return "weftkit: " + snapshot.state() + ", modules " + snapshot.moduleState()
                + ", " + snapshot.components().size() + " components, combined load time "
                + snapshot.totalLoadTime().toMillis() + " ms (may include nested loads)";
    }

    private static void sendComponents(CommandSender sender, List<Component> matches) {
        if (matches.isEmpty()) {
            sender.sendMessage("No matching components.");
            return;
        }
        matches.stream().limit(MAX_ROWS).map(DiagnosticsCommand::describe).forEach(sender::sendMessage);
        if (matches.size() > MAX_ROWS)
            sender.sendMessage(
                    "Showing " + MAX_ROWS + " of " + matches.size() + " matches; narrow the class-name filter.");
    }

    private static String describe(Component component) {
        StringJoiner detail = new StringJoiner(" ")
                .add(component.type().getName() + ":")
                .add(component.scope().name())
                .add(component.state().name());
        if (component.module()) detail.add("module");
        component
                .flag()
                .ifPresent(flag -> detail.add("flag=" + flag.key())
                        .add("enabled="
                                + component.enabled().map(Object::toString).orElse("not evaluated")));
        component.loadTime().ifPresent(time -> detail.add("load=" + time.toMillis() + " ms"));
        component.failure().ifPresent(failure -> detail.add("failure=" + failure.type()));
        return detail.toString();
    }
}
