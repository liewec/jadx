package com.jadx.dexeditor.model;

public class SearchResult {
    public static final int KIND_CLASS = 0;
    public static final int KIND_METHOD = 1;
    public static final int KIND_STRING = 2;

    private final int kind;
    private final String title;
    private final String subtitle;
    private final String classType;

    public SearchResult(int kind, String title, String subtitle, String classType) {
        this.kind = kind;
        this.title = title;
        this.subtitle = subtitle;
        this.classType = classType;
    }

    public int getKind() {
        return kind;
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
