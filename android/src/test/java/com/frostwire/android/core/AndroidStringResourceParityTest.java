package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidStringResourceParityTest {

    private static final Pattern STRING_NAME_PATTERN = Pattern.compile(
            "<string\\s+name=\"([^\"]+)\"[^>]*/>|<string\\s+name=\"([^\"]+)\"[^>]*>.*?</string>",
            Pattern.DOTALL);

    @Test
    public void localizedStringFilesMatchBaseStringKeys() throws Exception {
        Path res = projectRoot().resolve("res");
        List<String> baseKeys = stringKeys(res.resolve("values/strings.xml"));
        Set<String> baseKeySet = new HashSet<>(baseKeys);
        List<String> errors = new ArrayList<>();

        try (java.util.stream.Stream<Path> paths = Files.list(res)) {
            paths.filter(path -> path.getFileName().toString().startsWith("values-"))
                    .map(path -> path.resolve("strings.xml"))
                    .filter(Files::exists)
                    .forEach(path -> {
                        try {
                            List<String> localizedKeys = stringKeys(path);
                            Set<String> localizedKeySet = new HashSet<>(localizedKeys);
                            if (localizedKeySet.size() != localizedKeys.size()) {
                                errors.add(path + " has duplicate string names");
                            }
                            for (String key : baseKeys) {
                                if (!localizedKeySet.contains(key)) {
                                    errors.add(path + " missing " + key);
                                }
                            }
                            for (String key : localizedKeySet) {
                                if (!baseKeySet.contains(key)) {
                                    errors.add(path + " has extra " + key);
                                }
                            }
                        } catch (IOException e) {
                            errors.add(path + " failed to read: " + e.getMessage());
                        }
                    });
        }

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void expectedLocaleCountDoesNotShrink() throws Exception {
        Path res = projectRoot().resolve("res");
        long count;
        try (java.util.stream.Stream<Path> paths = Files.list(res)) {
            count = paths.filter(path -> path.getFileName().toString().startsWith("values-"))
                    .filter(path -> Files.exists(path.resolve("strings.xml")))
                    .count();
        }
        assertEquals(37, count);
    }

    private static List<String> stringKeys(Path path) throws IOException {
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Matcher matcher = STRING_NAME_PATTERN.matcher(source);
        List<String> keys = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            keys.add(key);
        }
        return keys;
    }

    private static Path projectRoot() {
        Path root = Path.of(System.getProperty("user.dir"));
        if (Files.exists(root.resolve("res/values/strings.xml"))) {
            return root;
        }
        return root.resolve("android");
    }
}
