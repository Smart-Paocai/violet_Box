package com.violet.box.ui.safety;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** User intent and recovery journal, independent of UI switches and command outcomes. */
final class GuardSelection {
    final Set<String> protect;
    final Set<String> restore;

    GuardSelection(Collection<String> protect, Collection<String> restore) {
        this.protect = new HashSet<>(protect);
        this.restore = new HashSet<>(restore);
        this.protect.removeAll(this.restore);
    }

    Set<String> managed() {
        Set<String> result = new HashSet<>(protect);
        result.addAll(restore);
        return result;
    }

    void request(Collection<String> group, boolean enable) {
        protect.removeAll(group);
        restore.removeAll(group);
        (enable ? protect : restore).addAll(group);
    }

    boolean wantsRestore(Collection<String> group) {
        for (String pkg : group) if (restore.contains(pkg)) return true;
        return false;
    }

    boolean manages(Collection<String> group) {
        for (String pkg : group) if (protect.contains(pkg) || restore.contains(pkg)) return true;
        return false;
    }

    boolean wantsProtection(Collection<String> group) {
        if (wantsRestore(group)) return false;
        for (String pkg : group) if (protect.contains(pkg)) return true;
        return false;
    }

    void restored(Collection<String> group) {
        protect.removeAll(group);
        restore.removeAll(group);
    }
}
