package com.csp.migration.model;

import java.util.Objects;

public class JsBlock {
    public enum Type {
        IMPORTED,
        INTERNAL,
        EVENT
    }

    private Type type;
    private String name;       // for IMPORTED scripts
    private int index;         // for INTERNAL scripts (1-based index)
    private String selector;   // for EVENT (e.g. ".csp_auto_0001" or ".btn")
    private String event;      // for EVENT (e.g. "click")
    private String content;    // JavaScript code block content

    public JsBlock() {}

    public JsBlock(Type type, String name, int index, String selector, String event, String content) {
        this.type = type;
        this.name = name;
        this.index = index;
        this.selector = selector;
        this.event = event;
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

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
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
        JsBlock jsBlock = (JsBlock) o;
        // Two blocks are equal if they are of the same type and have the same core content.
        // For IMPORTED, they must also match in name.
        // For EVENT, they must also match in selector and event.
        if (type != jsBlock.type) return false;
        if (type == Type.IMPORTED) {
            return Objects.equals(name, jsBlock.name) && Objects.equals(content, jsBlock.content);
        } else if (type == Type.EVENT) {
            return Objects.equals(selector, jsBlock.selector) && Objects.equals(event, jsBlock.event) && Objects.equals(content, jsBlock.content);
        } else { // INTERNAL
            return Objects.equals(content, jsBlock.content);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, selector, event, content);
    }

    @Override
    public String toString() {
        return "JsBlock{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", index=" + index +
                ", selector='" + selector + '\'' +
                ", event='" + event + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                '}';
    }
}
