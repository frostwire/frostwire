package org.limewire.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

public class RemoveFuzzyFromMessageBundlesCommand {
    public static void main(String[] args) {
        File[] listFiles = new File("/Users/gubatron/workspace.frostwire/frostwire.desktop/lib/messagebundles/").listFiles((arg0, arg1) -> arg1.endsWith(".po"));
        for (File f : listFiles) {
            try {
                System.out.println("Removing fuzzy from " + f.getName());
                removeFuzzy(f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void removeFuzzy(File f) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        String line = null;
        List<String> lines = new LinkedList<>();
        while ((line = br.readLine()) != null) {
            if (!line.startsWith("#, fuzzy")) {
                lines.add(line);
            }
        }
        br.close();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
        for (String ln : lines) {
            pw.println(ln);
        }
        pw.flush();
        pw.close();
    }
}