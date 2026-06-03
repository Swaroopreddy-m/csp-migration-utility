package com.csp.migration.model;

import java.util.Objects;

public class CssBlock {
    public enum Type {
        IMPORTED,
        INTERNAL,
        INLINE
    }

    private Type type;
    private String name;       // for IMPORTED stylesheets
    private int index;         // for INTERNAL styles (1-based index)
    private String selector;   // for INLINE style rules (e.g. ".csp_auto_0001" or ".box")
    private String content;    // CSS code content

    public CssBlock() {}

    public CssBlock(Type type, String name, int index, String selector, String content) {
        this.type = type;
        this.name = name;
        this.index = index;
        this.selector = selector;
        this.content = content != null ? content.trim() : "";
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content != null ? content.trim() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CssBlock cssBlock = (CssBlock) o;
        // Two blocks are equal if they are of the same type and have the same core content.
        // For IMPORTED, they must also match in name.
        // For INLINE, they must also match in selector.
        if (type != cssBlock.type) return false;
        if (type == Type.IMPORTED) {
            return Objects.equals(name, cssBlock.name) && Objects.equals(content, cssBlock.content);
        } else if (type == Type.INLINE) {
            return Objects.equals(selector, cssBlock.selector) && Objects.equals(content, cssBlock.content);
        } else { // INTERNAL
            return Objects.equals(content, cssBlock.content);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, selector, content);
    }

    @Override
    public String toString() {
        return "CssBlock{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", index=" + index +
                ", selector='" + selector + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                '}';
    }
}
