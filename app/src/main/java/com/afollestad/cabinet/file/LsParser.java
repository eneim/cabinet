package com.afollestad.cabinet.file;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LsParser {

    private static LsParser parser;

    private final List<FileInfo> mFileInfos;

    private LsParser() {
        mFileInfos = new ArrayList<>();
    }

    public synchronized static LsParser parse(final String path, final List<String> cmdOutput) {
        if (parser == null)
            parser = new LsParser();
        synchronized (parser.mFileInfos) {
            parser.mFileInfos.clear();
            for (String line : cmdOutput) {
                if (line.startsWith("lstat \'" + path) && line.contains("\' failed: Permission denied")) {
                    line = line.replace("lstat \'" + path, "");
                    line = line.replace("\' failed: Permission denied", "");
                    if (line.startsWith("/")) {
                        line = line.substring(1);
                    }
                    FileInfo failedToRead = new FileInfo(false, line);
                    parser.mFileInfos.add(failedToRead);
                    continue;
                }
                try {
                    FileInfo fileInfo = parser.processLine(line);
                    parser.mFileInfos.add(fileInfo);
                } catch (NumberFormatException | NullPointerException e) {
                    FileInfo failedToRead = new FileInfo(false, "");
                    parser.mFileInfos.add(failedToRead);
                }
            }
        }
        return parser;
    }

    private FileInfo processLine(String line) {
        final String[] split = line.split(" ");
        int index = 0;

        FileInfo file = new FileInfo(false, "");

        String date = "";
        String time = "";

        for (String token : split) {
            if (token.trim().isEmpty())
                continue;
            switch (index) {
                case 0: {
                    file.permissions = token;
                    break;
                }
                case 1: {
                    file.owner = token;
                    break;
                }
                case 2: {
                    file.group = token;
                    break;
                }
                case 3: {
                    if (token.contains("-")) {
                        // No length, this is the date
                        file.size = -1;
                        date = token;
                    } else if (token.contains(",")) {
                        //In /dev, ls lists the major and minor device numbers
                        file.size = -2;
                    } else {
                        // Length, this is a file
                        file.size = Long.parseLong(token);
                    }
                    break;
                }
                case 4: {
                    if (file.size == -1) {
                        // This is the time
                        time = token;
                    } else {
                        // This is the date
                        date = token;
                    }
                    break;
                }
                case 5: {
                    if (file.size == -2) {
                        date = token;
                    } else if (file.size > -1) {
                        time = token;
                    }
                    break;
                }
                case 6:
                    if (file.size == -2) {
                        time = token;
                    }
                    break;
            }
            index++;
        }

        if (line.length() > 0) {
            final String nameAndLink = line.substring(line.indexOf(time) + time.length() + 1);
            if (nameAndLink.contains(" -> ")) {
                final String[] splitSl = nameAndLink.split(" -> ");
                file.name = splitSl[0];
                file.linkedPath = splitSl[1];
            } else {
                file.name = nameAndLink;
            }
        }

        try {
            file.lastModified = new SimpleDateFormat("yyyy-MM-ddHH:mm", Locale.getDefault())
                    .parse(date + time).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            file.lastModified = 0;
        }

        file.readAvailable = true;
        file.type = file.permissions.charAt(0);
        file.directoryFileCount = "";

        return file;
    }

    public List<FileInfo> getFileInfos() {
        return new ArrayList<>(mFileInfos);
    }
}
