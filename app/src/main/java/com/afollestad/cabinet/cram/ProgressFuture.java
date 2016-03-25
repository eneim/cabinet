package com.afollestad.cabinet.cram;

/**
 * @author Aidan Follestad (afollestad)
 */
public interface ProgressFuture<W, T> {

    void progress(W source, T writing);
}
