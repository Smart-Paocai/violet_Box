package com.violet.box.ui.safety;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import static org.junit.Assert.*;

public class GuardSelectionTest {
    @Test public void failedRestoreRemainsRecoverableAndIsNotReapplied() {
        GuardSelection selection = new GuardSelection(Collections.singleton("app.a"), Collections.emptySet());
        selection.request(Collections.singleton("app.a"), false);
        // A lost Binder result/connection must not change the durable intent.
        GuardSelection reconnected = new GuardSelection(selection.protect, selection.restore);
        assertTrue(reconnected.managed().contains("app.a"));
        assertTrue(reconnected.wantsRestore(Collections.singleton("app.a")));
        assertFalse(reconnected.protect.contains("app.a"));
        assertFalse(reconnected.wantsProtection(Collections.singleton("app.a")));
        reconnected.restored(Collections.singleton("app.a"));
        assertTrue(reconnected.managed().isEmpty());
    }

    @Test public void sharedUidIsEnabledAndRestoredAsOneGroup() {
        GuardSelection selection = new GuardSelection(Collections.emptySet(), Collections.emptySet());
        java.util.List<String> shared = Arrays.asList("app.a", "app.b");
        selection.request(shared, true);
        assertTrue(selection.protect.containsAll(shared));
        selection.request(shared, false);
        assertTrue(selection.protect.isEmpty());
        assertTrue(selection.restore.containsAll(shared));
        selection.request(shared, true);
        assertTrue(selection.restore.isEmpty());
        assertTrue(selection.wantsProtection(shared));
        selection.request(shared, false);
        selection.restored(shared);
        assertTrue(selection.managed().isEmpty());
    }

    @Test public void restoreAllIncludesFailedAndInvisibleRows() {
        GuardSelection selection = new GuardSelection(Arrays.asList("app.visible", "app.hidden"),
                Collections.singleton("app.failed"));
        selection.request(selection.managed(), false);
        selection.restored(Collections.singleton("app.visible"));
        assertEquals(Set.of("app.hidden", "app.failed"), selection.managed());
        assertTrue(selection.protect.isEmpty());
    }

    @Test public void pendingRestoreWinsOverConflictingSavedProtection() {
        GuardSelection selection = new GuardSelection(Arrays.asList("app.a", "app.b"),
                Collections.singleton("app.b"));
        assertTrue(selection.wantsRestore(Arrays.asList("app.a", "app.b")));
        assertFalse(selection.protect.contains("app.b"));
    }
}
