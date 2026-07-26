package com.jadx.dexeditor.widget;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.widget.EditText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XML 语法高亮 + 颜色常量识别。
 * <p>
 * 支持高亮：
 * - XML 声明 / 处理指令 <?...?>
 * - DOCTYPE / CDATA
 * - 注释 <!-- -->
 * - 标签名 / 命名空间 / 属性名 / 属性值
 * - 实体引用 &amp; &#123; &#x7f;
 * - 颜色常量 #RRGGBB / #AARRGGBB（属性值中识别并取色）
 * <p>
 * 括号配对、自动补全由 XmlAutoComplete 处理。
 */
public class XmlSyntaxHighlighter implements TextWatcher {

    private final EditText editText;
    private boolean selfEditing = false;

    // 颜色（ARGB）
    private final int colorTag;        // 标签名
    private final int colorAttr;       // 属性名
    private final int colorValue;      // 属性值
    private final int colorComment;    // 注释
    private final int colorDecl;       // 声明/处理指令
    private final int colorEntity;     // 实体
    private final int colorBracket;    // 括号
    private final int colorColor;      // 颜色常量

    public XmlSyntaxHighlighter(EditText editText, int colorTag, int colorAttr, int colorValue,
                                int colorComment, int colorDecl, int colorEntity,
                                int colorBracket, int colorColor) {
        this.editText = editText;
        this.colorTag = colorTag;
        this.colorAttr = colorAttr;
        this.colorValue = colorValue;
        this.colorComment = colorComment;
        this.colorDecl = colorDecl;
        this.colorEntity = colorEntity;
        this.colorBracket = colorBracket;
        this.colorColor = colorColor;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (selfEditing || s == null) return;
        selfEditing = true;
        try {
            highlight((Spannable) s);
        } finally {
            selfEditing = false;
        }
    }

    private void highlight(Spannable s) {
        // 清除已有 span
        CharacterStyle[] spans = s.getSpans(0, s.length(), CharacterStyle.class);
        for (CharacterStyle sp : spans) {
            s.removeSpan(sp);
        }
        String text = s.toString();

        // 1. 注释 <!-- -->
        apply(s, text, Pattern.compile("<!--[\\s\\S]*?-->"), colorComment);
        // 2. XML 声明 <?xml ...?>
        apply(s, text, Pattern.compile("<\\?xml[\\s\\S]*?\\?>"), colorDecl);
        // 3. 处理指令 <?...?>
        apply(s, text, Pattern.compile("<\\?[^?]*\\?>"), colorDecl);
        // 4. DOCTYPE
        apply(s, text, Pattern.compile("<![Dd][Oo][Cc][Tt][Yy][Pp][Ee][\\s\\S]*?>"), colorDecl);
        // 5. CDATA
        apply(s, text, Pattern.compile("<!\\[CDATA\\[[\\s\\S]*?\\]\\]>"), colorDecl);
        // 6. 实体 &xxx; / &#123; / &#x7f;
        apply(s, text, Pattern.compile("&[a-zA-Z]+;|&#[0-9]+;|&#x[0-9a-fA-F]+;"), colorEntity);
        // 7. 标签：<(\/?)([\w:.-]+)
        Matcher mTag = Pattern.compile("<(/?)([\\w:.-]+)").matcher(text);
        while (mTag.find()) {
            int start = mTag.start(2);
            int end = mTag.end(2);
            s.setSpan(new ForegroundColorSpan(colorTag), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // 8. 属性名（标签内） attrName=
        Matcher mAttr = Pattern.compile("(\\s)([\\w:.-]+)\\s*=").matcher(text);
        while (mAttr.find()) {
            // 排除注释/CDATA 内
            if (isInside(text, mAttr.start(), "<!--", "-->")) continue;
            if (isInside(text, mAttr.start(), "<![CDATA[", "]]>")) continue;
            int start = mAttr.start(2);
            int end = mAttr.end(2);
            s.setSpan(new ForegroundColorSpan(colorAttr), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // 9. 属性值 "..." 内的颜色常量 #RRGGBB
        Matcher mVal = Pattern.compile("\"([^\"]*)\"").matcher(text);
        while (mVal.find()) {
            int vs = mVal.start(1);
            int ve = mVal.end(1);
            // 整个属性值染浅色
            s.setSpan(new ForegroundColorSpan(colorValue), mVal.start(), mVal.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            // 内部颜色常量
            Matcher mColor = Pattern.compile("#([a-fA-F0-9]{3,8})").matcher(text.substring(vs, ve));
            while (mColor.find()) {
                int cs = vs + mColor.start();
                int ce = vs + mColor.end();
                s.setSpan(new ForegroundColorSpan(colorColor), cs, ce,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new RelativeSizeSpan(1.05f), cs, ce,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        // 10. 括号 < > / { } / [ ] ( )
        Matcher mBrk = Pattern.compile("[<>{}\\[\\]()]").matcher(text);
        while (mBrk.find()) {
            // 跳过被注释覆盖的
            if (isInside(text, mBrk.start(), "<!--", "-->")) continue;
            s.setSpan(new ForegroundColorSpan(colorBracket), mBrk.start(), mBrk.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static boolean isInside(String text, int pos, String open, String close) {
        // 向前查找最近的 open
        int lastOpen = -1;
        int idx = 0;
        while (true) {
            int found = text.indexOf(open, idx);
            if (found < 0 || found >= pos) break;
            lastOpen = found;
            idx = found + open.length();
        }
        if (lastOpen < 0) return false;
        int afterClose = text.indexOf(close, lastOpen + open.length());
        return afterClose >= 0 && pos < afterClose;
    }

    private static void apply(Spannable s, String text, Pattern p, int color) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            s.setSpan(new ForegroundColorSpan(color), m.start(), m.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public void refresh() {
        Editable e = editText.getText();
        if (e != null) afterTextChanged(e);
    }
}
