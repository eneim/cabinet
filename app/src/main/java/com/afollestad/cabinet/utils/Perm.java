package com.afollestad.cabinet.utils;

import com.afollestad.cabinet.fragments.DetailsDialog;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Perm {

    public static final int READ = 4;
    public static final int WRITE = 2;
    public static final int EXECUTE = 1;

    public static String parse(String permLine, final DetailsDialog dialog) {
        if (permLine == null || permLine.trim().isEmpty()) return "Unknown";
        int owner = 0;
        if (permLine.charAt(1) == 'r') {
            owner += READ;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.ownerR.setChecked(true);
                    }
                });
            }
        }
        if (permLine.charAt(2) == 'w') {
            owner += WRITE;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.ownerW.setChecked(true);

                    }
                });
            }
        }
        if (permLine.charAt(3) == 'x') {
            owner += EXECUTE;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.ownerX.setChecked(true);
                    }
                });
            }
        }
        int group = 0;
        if (permLine.charAt(4) == 'r') {
            group += READ;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.groupR.setChecked(true);
                    }
                });
            }
        }
        if (permLine.charAt(5) == 'w') {
            group += WRITE;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.groupW.setChecked(true);
                    }
                });
            }
        }
        if (permLine.charAt(6) == 'x') {
            group += EXECUTE;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.groupX.setChecked(true);
                    }
                });
            }
        }
        int world = 0;
        if (permLine.charAt(7) == 'r') {
            world += READ;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.otherR.setChecked(true);
                    }
                });
            }
        }
        if (permLine.charAt(8) == 'w') {
            world += WRITE;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.otherW.setChecked(true);
                    }
                });
            }
        }
        if (permLine.charAt(9) == 'x') {
            world += EXECUTE;
            if (dialog != null) {
                dialog.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.otherX.setChecked(true);
                    }
                });
            }
        }
        return owner + "" + group + "" + world;
    }
}