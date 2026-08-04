package org.weftkit.wiring.runtime.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Provides;
import org.weftkit.wiring.Qualified;
import org.weftkit.wiring.Registry;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.StaticHolder;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.runtime.WeftLoader;

public final class Sample {

    private Sample() {}

    public static final class Probe {
        public final List<String> loads = new ArrayList<>();
        public final List<String> unloads = new ArrayList<>();
        public boolean proceed = true;
        public boolean throwOnLoad = false;
        public boolean faultOnUnload = false;
        public boolean explode = false;
        public int flagValue;
    }

    public static final class Widget {}

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

    @Registry
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

    @Wired
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
    public static final class Worker implements Service {
        public Worker() {}
    }

    public interface Cache {}

    @Wired
    public static final class LoaderUser {
        private final WeftLoader loader;

        public LoaderUser(WeftLoader loader) {
            this.loader = loader;
        }

        public WeftLoader loader() {
            return loader;
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
        private final Beta beta;
        private final Probe probe;

        public Gate(Beta beta, Probe probe) {
            this.beta = beta;
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
        private final WeftLoader loader;
        private final Gadget gadget = new Gadget();
        public Gadget observed;

        public SelfProvider(WeftLoader loader) {
            this.loader = loader;
        }

        public boolean load() {
            // Resolve a fresh component that needs our own product during our own load hook
            observed = loader.create(GadgetHolder.class).gadget();
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
}
