package com.jadx.dexeditor.model;

import com.android.tools.smali.dexlib2.iface.ClassDef;

import java.util.ArrayList;
import java.util.List;

public class ClassNode {
    private ClassDef classDef;
    private List<ClassNode> children = new ArrayList<>();
    private ClassNode parent;
    private String label;

    public ClassNode(ClassDef classDef) {
        this.classDef = classDef;
    }

    public ClassNode(String label) {
        this.label = label;
    }

    public ClassDef getClassDef() {
        return classDef;
    }

    public List<ClassNode> getChildren() {
        return children;
    }

    public ClassNode getParent() {
        return parent;
    }

    public void setParent(ClassNode parent) {
        this.parent = parent;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        if (classDef != null) {
            return classDef.getType();
        }
        return null;
    }

    public String getSimpleName() {
        if (classDef != null) {
            String type = classDef.getType();
            if (type == null) {
                return "";
            }
            return simpleNameFromDescriptor(type);
        }
        return label;
    }

    private static String simpleNameFromDescriptor(String descriptor) {
        String s = descriptor;
        if (s.startsWith("L") && s.endsWith(";") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        int idx = s.lastIndexOf('/');
        if (idx >= 0) {
            s = s.substring(idx + 1);
        }
        int dollar = s.lastIndexOf('$');
        if (dollar >= 0) {
            s = s.substring(dollar + 1);
        }
        return s;
    }

    public void addChild(ClassNode child) {
        child.setParent(this);
        children.add(child);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }
}
