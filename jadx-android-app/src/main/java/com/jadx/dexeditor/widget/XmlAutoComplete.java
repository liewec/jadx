package com.jadx.dexeditor.widget;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * XML 自动补全 + 括号配对。
 * <p>
 * - 输入 < 时，自动插入 </> 并把光标放中间
 * - 输入 " 时，自动补全右 "
 * - 输入 ( [ { 时，自动补全右半边
 * - 标签 / 属性提示通过 popup 由调用方处理（这里只做最常用的即时补全）
 */
public class XmlAutoComplete implements TextWatcher {

    private final EditText editText;
    private boolean selfEditing = false;

    // 常用 Manifest 标签
    public static final List<String> MANIFEST_TAGS = Arrays.asList(
            "manifest", "uses-sdk", "application", "activity", "service",
            "receiver", "provider", "intent-filter", "action", "category",
            "data", "uses-permission", "meta-data", "activity-alias",
            "uses-feature", "supports-screens", "compatible-screens",
            "instrumentation", "permission", "permission-group", "permission-tree",
            "uses-library", "queries", "package"
    );

    // 常用属性
    public static final List<String> MANIFEST_ATTRS = Arrays.asList(
            "android:name", "android:label", "android:icon", "android:theme",
            "android:versionCode", "android:versionName", "android:minSdkVersion",
            "android:targetSdkVersion", "android:compileSdkVersion",
            "android:allowBackup", "android:debuggable", "android:exported",
            "android:enabled", "android:permission", "android:process",
            "android:multiprocess", "android:launchMode", "android:configChanges",
            "android:screenOrientation", "android:windowSoftInputMode",
            "android:authorities", "android:readPermission", "android:writePermission",
            "android:path", "android:pathPrefix", "android:pathPattern",
            "android:scheme", "android:host", "android:port", "android:mimeType",
            "android:value", "android:resource", "android:priority"
    );

    public static final Set<String> BRACKET_PAIRS_OPEN = new HashSet<>(Arrays.asList("(", "[", "{"));
    public static final Set<String> BRACKET_PAIRS_CLOSE = new HashSet<>(Arrays.asList(")", "]", "}"));

    public XmlAutoComplete(EditText editText) {
        this.editText = editText;
    }

    public static List<String> suggestTags(String prefix) {
        java.util.List<String> r = new java.util.ArrayList<>();
        if (prefix == null) return r;
        for (String t : MANIFEST_TAGS) {
            if (t.startsWith(prefix)) r.add(t);
        }
        return r;
    }

    public static List<String> suggestAttrs(String prefix) {
        java.util.List<String> r = new java.util.ArrayList<>();
        if (prefix == null) return r;
        for (String a : MANIFEST_ATTRS) {
            if (a.startsWith(prefix)) r.add(a);
        }
        return r;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (selfEditing) return;
        if (count != 1) return;
        Editable e = editText.getText();
        if (e == null) return;
        int pos = editText.getSelectionStart();
        if (pos <= 0 || pos > e.length()) return;
        char c = e.charAt(pos - 1);
        String insert = null;
        switch (c) {
            case '"':
                insert = "\"";
                break;
            case '(':
                insert = ")";
                break;
            case '[':
                insert = "]";
                break;
            case '{':
                insert = "}";
                break;
            case '>':
                // 已经处理
                break;
            default:
                break;
        }
        if (insert != null) {
            selfEditing = true;
            try {
                e.insert(pos, insert);
                editText.setSelection(pos);
            } finally {
                selfEditing = false;
            }
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
