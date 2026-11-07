package org.weftkit.wiring.bukkit.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.registry.Flag;
import org.weftkit.wiring.registry.TestRegistry;
import org.weftkit.wiring.runtime.WeftLoader;

class DiagnosticsCommandTest {
    private final List<String> messages = new ArrayList<>();

    private CommandSender sender(boolean allowed) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(), new Class<?>[] {CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        assertEquals(DiagnosticsCommand.PERMISSION, args[0]);
                        return allowed;
                    }
                    if (method.getName().equals("sendMessage")) {
                        messages.add((String) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void deniesAccessBeforeAttemptingToReadTheGraph() {
        assertTrue(new DiagnosticsCommand(new WeftLoader(new TestRegistry()).diagnostics())
                .onCommand(sender(false), null, "debug", new String[0]));
        assertEquals(List.of("You do not have permission to view diagnostics."), messages);
    }

    @Test
    void reportsSummaryAndFiltersWithoutConstructingComponents() {
        WeftLoader loader = new WeftLoader(new TestRegistry().lazy(String.class, args -> {
            fail("Diagnostics must not materialize a lazy component");
            return null;
        }));
        assertTrue(loader.load());
        DiagnosticsCommand command = new DiagnosticsCommand(loader.diagnostics());
        command.onCommand(sender(true), null, "debug", new String[] {"components", "STRING"});
        assertTrue(messages.get(0).contains("LOADED"));
        assertTrue(messages.get(1).contains("java.lang.String: LAZY NOT_CREATED"));
        messages.clear();
        command.onCommand(sender(true), null, "debug", new String[] {"failures"});
        assertEquals("No matching components.", messages.get(1));
        messages.clear();
        command.onCommand(sender(true), null, "debug", new String[] {"invalid"});
        assertTrue(messages.get(0).startsWith("Usage: /debug"));
    }

    @Test
    void sectionsSelectTheirOwnComponentsAndAcceptMixedCase() {
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(String.class, args -> "loaded")
                .module(String.class)
                .flag(String.class, new Flag("sample", true))
                .plain(Integer.class, args -> 1));
        assertTrue(loader.load());
        DiagnosticsCommand command = new DiagnosticsCommand(loader.diagnostics());
        for (String section : List.of("MoDuLeS", "FLAGS", "timings")) {
            messages.clear();
            command.onCommand(sender(true), null, "debug", new String[] {section});
            assertEquals(2, messages.size());
            assertTrue(messages.get(1).startsWith("java.lang.String:"));
        }
        messages.clear();
        command.onCommand(sender(true), null, "debug", new String[] {"SuMmArY"});
        assertEquals(1, messages.size());
        loader.unload();
    }

    @Test
    void capsRowsAndReportsTheTotalMatchCount() {
        TestRegistry registry = new TestRegistry();
        for (int dimensions = 1; dimensions <= 55; dimensions++) {
            Class<?> type = Array.newInstance(String.class, new int[dimensions]).getClass();
            registry.lazy(type, args -> {
                fail("Listing must not construct components");
                return null;
            });
        }
        WeftLoader loader = new WeftLoader(registry);
        assertTrue(loader.load());
        new DiagnosticsCommand(loader.diagnostics())
                .onCommand(sender(true), null, "debug", new String[] {"components"});
        assertEquals(52, messages.size());
        assertEquals("Showing 50 of 55 matches; narrow the class-name filter.", messages.get(51));
        loader.unload();
    }
}
