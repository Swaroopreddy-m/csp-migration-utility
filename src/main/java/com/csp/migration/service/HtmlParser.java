package com.csp.migration.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.File;
import java.io.IOException;

public class HtmlParser {

    public static Document parse(File file) throws IOException {
        return Jsoup.parse(file, "UTF-8");
    }

    public static Document parse(String html) {
        return Jsoup.parse(html);
    }
}
