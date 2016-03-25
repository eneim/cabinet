package com.afollestad.cabinet.comparators;

import android.content.Context;
import android.preference.PreferenceManager;

import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.utils.AlphanumComparator;

import java.util.Comparator;

/**
 * Sorts files by extension, alphabetically. Folders will be at the beginning.
 *
 * @author Aidan Follestad (afollestad)
 */
public class ExtensionComparator implements Comparator<File> {

    private boolean foldersFirst;

    public ExtensionComparator(Context context) {
        if (context != null)
            foldersFirst = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("folders_first", true);
    }

    @Override
    public int compare(File lhs, File rhs) {
        if (foldersFirst) {
            if (lhs.isDirectory() && !rhs.isDirectory()) {
                return -1;
            } else if (lhs.isDirectory() == rhs.isDirectory()) {
                // Sort my extension once sorted by folders
                final int extensionCompare = AlphanumComparator.compare(lhs.getExtension(), rhs.getExtension());
                if (extensionCompare == 0) {
                    // If the extensions are the same, sort by name
                    return AlphanumComparator.compare(lhs.getName(), rhs.getName());
                }
                return extensionCompare;
            } else {
                return 1;
            }
        } else {
            final int extensionCompare = AlphanumComparator.compare(lhs.getExtension(), rhs.getExtension());
            if (extensionCompare == 0) {
                // If the extensions are the same, sort by name
                return AlphanumComparator.compare(lhs.getName(), rhs.getName());
            }
            return extensionCompare;
        }
    }
}