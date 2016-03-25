package com.afollestad.cabinet.cram;

import com.afollestad.cabinet.file.base.File;

import org.apache.commons.compress.archivers.ArchiveEntry;

import java.util.Comparator;

/**
 * @author Aidan Follestad (afollestad)
 */
class EntryComparator implements Comparator<ArchiveEntry> {

    int countOccurrences(String str, char find) {
        if (str == null || str.length() == 0) return 0;
        if (str.endsWith(File.separator))
            str = str.substring(0, str.length() - 1);
        int count = 0;
        final char[] cArr = str.toCharArray();
        for (char c : cArr) {
            if (c == find)
                count++;
        }
        return count;
    }

    @Override
    public int compare(ArchiveEntry lhs, ArchiveEntry rhs) {
        if (lhs.isDirectory() && !rhs.isDirectory()) {
            return -1;
        } else if (!lhs.isDirectory() && rhs.isDirectory()) {
            return 1;
        } else {
            int leftSlashes = countOccurrences(lhs.getName(), File.separatorChar);
            int rightSlashes = countOccurrences(rhs.getName(), File.separatorChar);
            if (leftSlashes < rightSlashes) return -1;
            else if (leftSlashes > rightSlashes) return 1;
            else return lhs.getName().compareTo(rhs.getName());
        }
    }
}