package org.weftkit.wiring.runtime.sample;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.weftkit.wiring.FeatureFlag;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.HandledBy;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.Provides;
import org.weftkit.wiring.Qualified;
import org.weftkit.wiring.Registry;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.StaticHolder;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.runtime.Weft;
import org.weftkit.wiring.runtime.WeftLoader;
import org.weftkit.wiring.runtime.WeftView;

public final class Sample {

    private Sample() {}

    public static final class Probe {
        public final List<String> loads = new ArrayList<>();
        public final List<String> unloads = new ArrayList<>();
        public boolean proceed = true;
        public boolean throwOnLoad = false;
        public boolean faultOnUnload = false;
        public boolean explode = false;
        public boolean lazyProceed = true;
        public boolean lazyThrowOnLoad = false;
        public boolean lazyNullProduct = false;
        public int flagValue;
        public final Set<String> enabledFlags = new HashSet<>();
        public final List<String> activations = new ArrayList<>();
        public final List<String> deactivations = new ArrayList<>();
        public boolean alphaModuleProceed = true;
        public boolean alphaModuleThrow = false;
        // Handed over by the tests, standing in for a plugin main passing the loader on
        public WeftLoader loader;
    }

    public static final class Widget {}

    @SuppressWarnings("deprecation")
    @StaticHolder
    static final class Flag {
        static int value;
    }

    public interface Service {}

    public interface Storage {}

    @Wired
    @Singleton
    public static final class SqlStorage implements Storage {
        public SqlStorage() {}
    }

    @Wired
    public static final class Backup {
        private final Storage storage;

        public Backup(Storage storage) {
            this.storage = storage;
        }

        public Storage storage() {
            return storage;
        }
    }

    @Registry(
            modules = {AlphaModule.class, ZuluModule.class},
            ambient = {Notifier.class, Cache.class, Storage.class})
    public static final class Wiring {
        private final Probe probe;

        public Wiring(Probe probe) {
            this.probe = probe;
        }

        public Probe probe() {
            return probe;
        }
    }

    @Wired
    @Singleton
    public static final class Alpha implements Loader {
        private final Probe probe;

        public Alpha(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Alpha");
            return true;
        }

        public void unload() {
            probe.unloads.add("Alpha");
        }
    }

    @Wired
    @Singleton
    public static final class Beta implements Loader {
        private final Alpha alpha;
        private final Wiring wiring;
        private final Probe probe;

        public Beta(Alpha alpha, Wiring wiring, Probe probe) {
            this.alpha = alpha;
            this.wiring = wiring;
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Beta");
            return true;
        }

        public void unload() {
            probe.unloads.add("Beta");
        }

        public Alpha alpha() {
            return alpha;
        }

        public Wiring wiring() {
            return wiring;
        }
    }

    @Wired
    @Singleton
    public static final class Provider implements Loader {
        private final Probe probe;
        private final Widget widget = new Widget();
        private final Widget spare = new Widget();

        public Provider(Probe probe) {
            if (probe.explode) throw new IllegalStateException("construction failed");
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Provider");
            return true;
        }

        public void unload() {
            probe.unloads.add("Provider");
            if (probe.faultOnUnload) throw new IllegalStateException("teardown failed");
        }

        @Provides
        public Widget widget() {
            return widget;
        }

        @Provides
        @Qualified("spare")
        public Widget spare() {
            return spare;
        }
    }

    @HandledBy(AlphaModule.class)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marked {
        String value() default "marked";

        int[] counts() default {1, 2};
    }

    @HandledBy(AlphaModule.class)
    @Repeatable(Tags.class)
    @Retention(RetentionPolicy.CLASS)
    public @interface Tagged {
        String value();
    }

    @Retention(RetentionPolicy.CLASS)
    public @interface Tags {
        Tagged[] value();
    }

    @HandledBy(AbsentModule.class)
    @Retention(RetentionPolicy.CLASS)
    public @interface Orphaned {}

    @HandledBy(AbsentModule.class)
    @Retention(RetentionPolicy.CLASS)
    public @interface Silenced {}

    @Wired
    @Orphaned
    public static final class Consumer implements Service {
        private final Widget widget;

        public Consumer(Widget widget) {
            this.widget = widget;
        }

        public Widget widget() {
            return widget;
        }
    }

    @Wired
    @Marked
    @Tagged("first")
    @Tagged("second")
    public static final class Worker implements Service {
        public Worker() {}
    }

    public interface Cache {}

    // Requires the declared ambient Cache, which most tests leave unfilled
    @Wired
    public static final class CacheUser {
        private final Cache cache;

        public CacheUser(Cache cache) {
            this.cache = cache;
        }

        public Cache cache() {
            return cache;
        }
    }

    @Wired
    public static final class Tuner {
        private final Optional<Widget> widget;
        private final Optional<Cache> cache;

        public Tuner(Optional<Widget> widget, Optional<Cache> cache) {
            this.widget = widget;
            this.cache = cache;
        }

        public Optional<Widget> widget() {
            return widget;
        }

        public Optional<Cache> cache() {
            return cache;
        }
    }

    @Wired
    public static final class SpareUser {
        private final Widget widget;

        public SpareUser(@Qualified("spare") Widget widget) {
            this.widget = widget;
        }

        public Widget widget() {
            return widget;
        }
    }

    @Wired
    @Singleton(lazy = true)
    public static final class Cold {
        public Cold(Probe probe) {
            probe.loads.add("Cold");
        }
    }

    @Wired
    public static final class ColdUser {
        private final Cold cold;

        public ColdUser(Cold cold) {
            this.cold = cold;
        }

        public Cold cold() {
            return cold;
        }
    }

    public interface Notifier {}

    @Wired
    @Singleton
    @Qualified("mail")
    public static final class MailNotifier implements Notifier {
        public MailNotifier() {}
    }

    @Wired
    @Singleton
    @Qualified("chat")
    public static final class ChatNotifier implements Notifier {
        public ChatNotifier() {}
    }

    @Wired
    public static final class Alerts {
        private final Notifier notifier;

        public Alerts(@Qualified("chat") Notifier notifier) {
            this.notifier = notifier;
        }

        public Notifier notifier() {
            return notifier;
        }
    }

    @Wired
    @Singleton
    public static final class Gate implements Loader {
        private final Probe probe;

        // The Beta parameter is not used. It orders the graph so Beta loads before Gate
        public Gate(Beta beta, Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Gate");
            if (probe.throwOnLoad) throw new IllegalStateException("load hook failed");
            return probe.proceed;
        }

        public void unload() {
            probe.unloads.add("Gate");
        }
    }

    @Wired
    @Singleton
    @Initializes(Flag.class)
    public static final class Initer implements Loader {
        private final Probe probe;

        public Initer(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Initer");
            Flag.value = 42;
            return true;
        }

        public void unload() {
            probe.unloads.add("Initer");
        }
    }

    public static final class Gadget {}

    @Wired
    public static final class GadgetHolder {
        private final Gadget gadget;

        public GadgetHolder(Gadget gadget) {
            this.gadget = gadget;
        }

        public Gadget gadget() {
            return gadget;
        }
    }

    @Wired
    @Singleton
    public static final class SelfProvider implements Loader {
        private final Probe probe;
        private final Gadget gadget = new Gadget();
        public Gadget observed;

        public SelfProvider(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            // Resolve a fresh component that needs our own product during our own load hook
            observed = probe.loader.create(GadgetHolder.class).gadget();
            return true;
        }

        @Provides
        public Gadget gadget() {
            return gadget;
        }
    }

    @Wired
    @Singleton
    @Requires(Flag.class)
    public static final class Reader implements Loader {
        private final Probe probe;

        public Reader(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Reader");
            probe.flagValue = Flag.value;
            return true;
        }

        public void unload() {
            probe.unloads.add("Reader");
        }
    }

    // The lazy members below have no eager dependents, so load() itself never materializes them

    public interface Chiller {}

    @Wired
    @Singleton(lazy = true)
    public static final class Frost implements Chiller, Loader {
        private final Probe probe;

        public Frost(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Frost");
            if (probe.lazyThrowOnLoad) throw new IllegalStateException("lazy load failed");
            return probe.lazyProceed;
        }

        public void unload() {
            probe.unloads.add("Frost");
        }
    }

    // Consumes Frost through its interface, so the registry records the Chiller binding
    @Wired
    public static final class ChillerUser {
        private final Chiller chiller;

        public ChillerUser(Chiller chiller) {
            this.chiller = chiller;
        }

        public Chiller chiller() {
            return chiller;
        }
    }

    @Wired
    public static final class FrostUser {
        private final Frost frost;

        public FrostUser(Frost frost) {
            this.frost = frost;
        }

        public Frost frost() {
            return frost;
        }
    }

    public static final class Gear {}

    @Wired
    @Singleton(lazy = true)
    public static final class GearProvider implements Loader {
        private final Probe probe;
        private final Gear gear = new Gear();
        private final Gear spare = new Gear();

        public GearProvider(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("GearProvider");
            return true;
        }

        public void unload() {
            probe.unloads.add("GearProvider");
        }

        @Provides
        public Gear gear() {
            return probe.lazyNullProduct ? null : gear;
        }

        @Provides
        @Qualified("spareGear")
        public Gear spareGear() {
            return spare;
        }
    }

    @Wired
    public static final class GearUser {
        private final Gear gear;

        public GearUser(Gear gear) {
            this.gear = gear;
        }

        public Gear gear() {
            return gear;
        }
    }

    @Wired
    public static final class SpareGearUser {
        private final Gear gear;

        public SpareGearUser(@Qualified("spareGear") Gear gear) {
            this.gear = gear;
        }

        public Gear gear() {
            return gear;
        }
    }

    @Wired
    public static final class OptionalGearUser {
        private final Optional<Gear> gear;

        public OptionalGearUser(Optional<Gear> gear) {
            this.gear = gear;
        }

        public Optional<Gear> gear() {
            return gear;
        }
    }

    @Wired
    @Singleton(lazy = true)
    public static final class Chain implements Loader {
        private final Probe probe;

        public Chain(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Chain");
            // Materialize another lazy singleton from inside the load hook
            probe.loader.create(FrostUser.class);
            return true;
        }

        public void unload() {
            probe.unloads.add("Chain");
        }
    }

    // Zulu sorts after Alpha, so activation order Zulu before Alpha proves injection drives it
    @WeftModule(id = "sample:zulu")
    public static final class ZuluModule implements ModuleLifecycle {
        private final Probe probe;

        public ZuluModule(Probe probe) {
            this.probe = probe;
        }

        @Override
        public boolean activate() {
            probe.activations.add("Zulu");
            return true;
        }

        @Override
        public void deactivate() {
            probe.deactivations.add("Zulu");
        }
    }

    @WeftModule(id = "sample:alpha")
    public static final class AlphaModule implements ModuleLifecycle {
        private final Probe probe;

        public AlphaModule(ZuluModule zulu, Probe probe) {
            this.probe = probe;
        }

        @Override
        public boolean activate() {
            probe.activations.add("Alpha");
            if (probe.alphaModuleThrow) throw new IllegalStateException("module activation failed");
            return probe.alphaModuleProceed;
        }

        @Override
        public void deactivate() {
            probe.deactivations.add("Alpha");
        }
    }

    @WeftModule(id = "sample:absent")
    public static final class AbsentModule {
        public AbsentModule() {}
    }

    public interface GatedService {}

    @Wired
    @Singleton
    @FeatureFlag("frozen")
    @Silenced
    public static final class Frozen implements GatedService, Loader {
        private final Probe probe;

        public Frozen(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("Frozen");
            return true;
        }
    }

    // Plain consumer that tolerates the flagged Frozen being gated off, through an optional dependency
    @Wired
    public static final class GatedUser {
        private final Optional<Frozen> frozen;

        public GatedUser(Optional<Frozen> frozen) {
            this.frozen = frozen;
        }

        public Optional<Frozen> frozen() {
            return frozen;
        }
    }

    // Eager consumer of the same optional flagged dependency, exercising the load-order path
    @Wired
    @Singleton
    public static final class GatedWatcher {
        private final Optional<Frozen> frozen;

        public GatedWatcher(Optional<Frozen> frozen) {
            this.frozen = frozen;
        }

        public Optional<Frozen> frozen() {
            return frozen;
        }
    }

    public static final class Tool {}

    // Flagged and lazy at once, so its product exercises the gate before materialization
    @Wired
    @Singleton(lazy = true)
    @FeatureFlag("tools")
    public static final class ToolProvider implements Loader {
        private final Probe probe;
        private final Tool tool = new Tool();

        public ToolProvider(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("ToolProvider");
            return true;
        }

        @Provides
        public Tool tool() {
            return tool;
        }
    }

    @Wired
    public static final class OptionalToolUser {
        private final Optional<Tool> tool;

        public OptionalToolUser(Optional<Tool> tool) {
            this.tool = tool;
        }

        public Optional<Tool> tool() {
            return tool;
        }
    }

    // Plain consumer of the ambient Probe, letting an explicit argument compete with it
    @Wired
    public static final class Reporter {
        private final Probe probe;

        public Reporter(Probe probe) {
            this.probe = probe;
        }

        public Probe probe() {
            return probe;
        }
    }

    @Wired
    @Singleton
    public static final class ProbeFlags implements FeatureFlags {
        private final Probe probe;

        public ProbeFlags(Probe probe) {
            this.probe = probe;
        }

        @Override
        public boolean isEnabled(String key) {
            return probe.enabledFlags.contains(key);
        }
    }

    @Wired
    public static final class WeftUser {
        private final Weft weft;
        private final WeftView view;

        public WeftUser(Weft weft, WeftView view) {
            this.weft = weft;
            this.view = view;
        }

        public Weft weft() {
            return weft;
        }

        public WeftView view() {
            return view;
        }
    }

    @SuppressWarnings("deprecation")
    @StaticHolder
    static final class LazyFlag {
        static int value;
    }

    @Wired
    @Singleton(lazy = true)
    @Initializes(LazyFlag.class)
    public static final class LazyIniter implements Loader {
        private final Probe probe;

        public LazyIniter(Probe probe) {
            this.probe = probe;
        }

        public boolean load() {
            probe.loads.add("LazyIniter");
            LazyFlag.value = 7;
            return true;
        }

        public void unload() {
            probe.unloads.add("LazyIniter");
        }
    }

    @Wired
    @Requires(LazyFlag.class)
    public static final class LazyReader {
        private final int seen;

        public LazyReader() {
            this.seen = LazyFlag.value;
        }

        public int seen() {
            return seen;
        }
    }
}
