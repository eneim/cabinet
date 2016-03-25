package com.afollestad.cabinet.cram;

import android.support.annotation.NonNull;

import java.util.Locale;

/**
 * @author Aidan Follestad (afollestad)
 */
public enum ArchiveType {

    Zip("application/zip"),
    Jar("application/java-archive"),
    Tar("application/x-tar"),
    CompressedTar("application/x-gzip"),
    SevenZip("application/x-7z-compressed"),
    Unsupported("(unsupported)");

    ArchiveType(String value) {
        mValue = value;
    }

    private final String mValue;

    @NonNull
    public String value() {
        return mValue;
    }

    @NonNull
    public static ArchiveType fromExtension(String extension) {
        extension = extension.toLowerCase(Locale.getDefault());
        switch (extension) {
            case "zip":
                return Zip;
            case "jar":
                return Jar;
            case "tar":
                return Tar;
            case "tar.gz":
                return CompressedTar;
            case "7z":
                return SevenZip;
            default:
                return Unsupported;
        }
    }

    @NonNull
    public static ArchiveType fromMime(String mime) {
        switch (mime) {
            case "application/zip":
                return Zip;
            case "application/vnd.android.package-archive":
                return Zip;
            case "application/java-archive":
                return Jar;
            case "application/x-tar":
                return Tar;
            case "application/x-gzip":
                return CompressedTar;
            case "application/x-7z-compressed":
                return SevenZip;
            default:
                return Unsupported;
        }
    }

    @Override
    public String toString() {
        return value();
    }
}