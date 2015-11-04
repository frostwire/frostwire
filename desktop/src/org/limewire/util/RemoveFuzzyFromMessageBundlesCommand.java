package org.limewire.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

public class RemoveFuzzyFromMessageBundlesCommand {
    
    public static void main(String[] args) {
        File[] listFiles = new File("/Users/gubatron/workspace.frostwire/frostwire.desktop/lib/messagebundles/").listFiles(new FilenameFilter() {
            
            @Override
            public boolean accept(File arg0, String arg1) {
                return arg1.endsWith(".po");
            }
        });
        
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
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));
        String line =null;
        List<String> lines = new LinkedList<String>();
        while ((line = br.readLine())!=null) {
            if (!line.startsWith("#, fuzzy")) {
                lines.add(line);
            }
        }
        br.close();
        
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
        for (String ln : lines) {
            pw.println(ln);
        }
        pw.flush();
        pw.close();
    }
}