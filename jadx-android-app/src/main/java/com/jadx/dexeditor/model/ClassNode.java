package com.jadx.dexeditor.model;

import java.util.ArrayList;
import java.util.List;

public class ClassNode {
    public static final int TYPE_PACKAGE = 0;
    public static final int TYPE_CLASS = 1;

    private final int type;
    private final String name;
    private final String classType;
    private final ClassNode parent;
    private final List<ClassNode> children = new ArrayList<>();
    private int depth;
    private boolean expanded;

    public ClassNode(int type, String name, String classType, ClassNode parent) {
        this.type = type;
        this.name = name;
        this.classType = classType;
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
        this.expanded = parent == null;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getClassType() {
        return classType;
    }

    public List<ClassNode> getChildren() {
        return children;
    }

    public ClassNode getParent() {
        return parent;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isPackage() {
        return type == TYPE_PACKAGE;
    }

    public boolean isClass() {
        return type == TYPE_CLASS;
    }

    public int countClasses() {
        if (type == TYPE_CLASS) {
            return 1;
        }
        int count = 0;
        for (ClassNode child : children) {
            count += child.countClasses();
        }
        return count;
    }
}
