package org.nrg.xsync.globus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.nrg.prefs.annotations.NrgPreference;

/**
 * Guard test for {@link GlobusProjectPrefs}. The {@code @NrgPreference}
 * framework registers each preference under a name <em>derived from its getter
 * method</em>, and the {@code KEY} constants passed to {@code set}/{@code
 * getValue} must equal those registered names. When they drift, {@code set}
 * throws {@code InvalidPreferenceName} — which the setters swallow — so the
 * preference silently fails to persist. This test fails fast on that drift.
 */
class GlobusProjectPrefsTest {

    @Test
    void preferenceKeyConstantsMatchNrgPreferenceGetterNames() {
        final Set<String> derivedFromGetters = Arrays.stream(GlobusProjectPrefs.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(NrgPreference.class) && method.getParameterCount() == 0)
                .map(GlobusProjectPrefsTest::preferenceName)
                .collect(Collectors.toCollection(TreeSet::new));

        final Set<String> keyConstants = Arrays.stream(GlobusProjectPrefs.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class
                        && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))
                .map(GlobusProjectPrefsTest::stringValue)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(keyConstants, derivedFromGetters,
                "Each @NrgPreference getter's derived name must equal a key constant and vice versa; "
                        + "a mismatch makes that preference silently fail to persist.");
    }

    /** Derive the preference name from a getter: strip {@code get}/{@code is}, lowercase the first char. */
    private static String preferenceName(final Method getter) {
        String name = getter.getName();
        if (name.startsWith("get")) {
            name = name.substring(3);
        } else if (name.startsWith("is")) {
            name = name.substring(2);
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String stringValue(final Field field) {
        try {
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
