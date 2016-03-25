package com.afollestad.cabinet.comparators;

import android.content.Context;
import android.preference.PreferenceManager;

import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.utils.AlphanumComparator;

import java.util.Comparator;

/**
 * Sorts files and folders by name, alphabetically.
 *
 * @author Aidan Follestad (afollestad)
 */
public class AlphabeticalComparator implements Comparator<File> {

    private boolean foldersFirst;

    public AlphabeticalComparator(Context context) {
        if (context != null)
            foldersFirst = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("folders_first", true);
    }

    public int compare(File lhs, File rhs) {
        if (foldersFirst) {
            if (lhs.isDirectory() && !rhs.isDirectory()) {
                return -1;
            } else if (lhs.isDirectory() == rhs.isDirectory()) {
                // Sort my name once sorted by folders
                return AlphanumComparator.compare(lhs.getName(), rhs.getName());
            } else {
                return 1;
            }
        } else {
            return AlphanumComparator.compare(lhs.getName(), rhs.getName());
        }
    }
}