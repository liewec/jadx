package com.jadx.dexeditor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * APK 资源条目的树形节点。
 * <p>
 * 将 APK 内的条目路径（如 {@code res/layout/main.xml}）按 {@code /} 拆分为多层节点，
 * 支持展开/折叠，便于以层级树展示。
 */
public final class EntryNode {

    public static final int TYPE_DIR = 0;
    public static final int TYPE_FILE = 1;

    private final int type;
    private final String name;          // 当前段名称，如 "layout"
    private final String fullPath;      // 完整路径，如 "res/layout/main.xml"；目录为 "res/layout/"
    private final int depth;
    private final List<EntryNode> children = new ArrayList<>();
    private boolean expanded;

    public EntryNode(int type, String name, String fullPath, int depth) {
        this.type = type;
        this.name = name;
        this.fullPath = fullPath;
        this.depth = depth;
    }

    public int getType() {
        return type;
    }

    public boolean isDir() {
        return type == TYPE_DIR;
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public int getDepth() {
        return depth;
    }

    public List<EntryNode> getChildren() {
        return children;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    /** 统计该目录下（递归）的文件数量 */
    public int countFiles() {
        int count = 0;
        for (EntryNode child : children) {
            if (child.isDir()) {
                count += child.countFiles();
            } else {
                count++;
            }
        }
        return count;
    }
}
