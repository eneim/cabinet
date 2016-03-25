package com.afollestad.cabinet.cram;

import com.afollestad.cabinet.file.base.File;

import java.util.Comparator;

/**
 * @author Aidan Follestad (afollestad)
 */
class SlashComparator implements Comparator<File> {

    public static int countOccurrences(String str) {
        if (str == null || str.length() == 0) return 0;
        if (str.endsWith(File.separator))
            str = str.substring(0, str.length() - 1);
        int count = 0;
        final char[] cArr = str.toCharArray();
        for (char c : cArr) {
            if (c == File.separatorChar)
                count++;
        }
        return count;
    }

    @Override
    public int compare(File lhs, File rhs) {
        int leftSlashes = countOccurrences(lhs.getPath());
        int rightSlashes = countOccurrences(rhs.getPath());
        if (leftSlashes < rightSlashes) return -1;
        else if (leftSlashes > rightSlashes) return 1;
        else return 0;
    }
}