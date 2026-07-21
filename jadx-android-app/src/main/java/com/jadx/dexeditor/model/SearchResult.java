package com.jadx.dexeditor.model;

public class SearchResult {
    public static final int MODE_CLASS = 0;
    public static final int MODE_METHOD = 1;
    public static final int MODE_STRING = 2;

    private final int mode;
    private final String title;
    private final String subtitle;
    private final String classType;

    public SearchResult(int mode, String title, String subtitle, String classType) {
        this.mode = mode;
        this.title = title;
        this.subtitle = subtitle;
        this.classType = classType;
    }

    public int getMode() {
        return mode;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getClassType() {
        return classType;
    }
}
