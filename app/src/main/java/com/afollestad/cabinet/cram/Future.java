package com.afollestad.cabinet.cram;

/**
 * @author Aidan Follestad (afollestad)
 */
public interface Future<W, T> {

    void complete(W source, T results, Exception e);
}
