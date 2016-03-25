package com.afollestad.cabinet.cram;

import com.afollestad.cabinet.file.base.File;

import org.apache.commons.compress.archivers.ArchiveEntry;

import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
class Util {

    public static void processEntry(ArchiveEntry ent, Map<String, CramEntry> toAdd) {
        if (!ent.getName().contains(File.separator) || !ent.isDirectory())
            toAdd.put(ent.getName(), new CramEntry(ent.getName(), ent.getSize()));
        if (ent.getName().contains(File.separator)) {
            String parent = ent.getName().substring(0, ent.getName().lastIndexOf(File.separatorChar));
            if (parent.contains(File.separator)) {
                // Build full path
                final String[] split = parent.split(File.separator);
                final StringBuilder pathBuilder = new StringBuilder();
                int index = 0;
                for (String sub : split) {
                    pathBuilder.append(sub);
                    pathBuilder.append(File.separator);
                    toAdd.put(pathBuilder.toString(),
                            new CramEntry(pathBuilder.toString(),
                                    index == split.length - 1 ? ent.getSize() : -1));
                    index++;
                }
            } else if (!parent.isEmpty()) {
                final String path = parent + File.separator;
                toAdd.put(path, new CramEntry(path, ent.getSize()));
            }
        }
    }

    public static int nthOccurrence(String str, char c, int n) {
        int pos = str.indexOf(c, 0);
        while (n-- > 0 && pos != -1)
            pos = str.indexOf(c, pos + 1);
        return pos;
    }
}