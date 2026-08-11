package com.masiton.ai.presentation.webhook;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.masiton.common.web.BusinessException;

final class YoutubeAtomNotificationParser {

    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final String YOUTUBE_NAMESPACE = "http://www.youtube.com/xml/schemas/2015";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    YoutubeAtomNotification parse(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw invalidPayload();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(payload)));
            Element root = document.getDocumentElement();
            if (!"feed".equals(root.getLocalName()) || !ATOM_NAMESPACE.equals(root.getNamespaceURI())) {
                throw invalidPayload();
            }
            String channelId = requiredIdentifier(document, "channelId");
            String videoId = requiredIdentifier(document, "videoId");
            String videoUrl = requiredAlternateVideoUrl(document);
            if (!videoId.equals(videoIdFrom(videoUrl))) {
                throw invalidPayload();
            }
            return new YoutubeAtomNotification(channelId, videoId, videoUrl);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPayload();
        }
    }

    private String requiredIdentifier(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(YOUTUBE_NAMESPACE, localName);
        if (nodes.getLength() != 1) {
            throw invalidPayload();
        }
        String value = nodes.item(0).getTextContent();
        if (value == null || !IDENTIFIER_PATTERN.matcher(value.trim()).matches()) {
            throw invalidPayload();
        }
        return value.trim();
    }

    private String requiredAlternateVideoUrl(Document document) {
        NodeList links = document.getElementsByTagNameNS(ATOM_NAMESPACE, "link");
        String videoUrl = null;
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            if ("alternate".equals(link.getAttribute("rel"))) {
                String href = link.getAttribute("href");
                if (href.isBlank() || videoUrl != null) {
                    throw invalidPayload();
                }
                videoUrl = href.trim();
            }
        }
        if (videoUrl == null) {
            throw invalidPayload();
        }
        videoIdFrom(videoUrl);
        return videoUrl;
    }

    private String videoIdFrom(String rawVideoUrl) {
        try {
            URI uri = URI.create(rawVideoUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if ("youtu.be".equals(host)) {
                return identifierFromPath(uri.getPath(), 1, 2);
            }
            if (!"youtube.com".equals(host) && !"www.youtube.com".equals(host)) {
                throw new IllegalArgumentException();
            }
            String path = uri.getPath();
            if ("/watch".equals(path)) {
                return videoIdFromQuery(uri.getRawQuery());
            }
            if (path != null && path.startsWith("/shorts/")) {
                return identifierFromPath(path, 2, 3);
            }
            if (path != null && path.startsWith("/embed/")) {
                return identifierFromPath(path, 2, 3);
            }
        } catch (IllegalArgumentException exception) {
            throw invalidPayload();
        }
        throw invalidPayload();
    }

    private String videoIdFromQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw invalidPayload();
        }
        String videoId = null;
        for (String pair : rawQuery.split("&", -1)) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = pair.substring(0, separator);
            if (!"v".equals(key)) {
                continue;
            }
            if (videoId != null) {
                throw invalidPayload();
            }
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
                throw invalidPayload();
            }
            videoId = value;
        }
        if (videoId == null) {
            throw invalidPayload();
        }
        return videoId;
    }

    private String identifierFromPath(String path, int identifierIndex, int expectedSegmentCount) {
        if (path == null) {
            throw invalidPayload();
        }
        String[] segments = path.split("/", -1);
        if (segments.length != expectedSegmentCount) {
            throw invalidPayload();
        }
        String value = segments[identifierIndex];
        if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw invalidPayload();
        }
        return value;
    }

    private BusinessException invalidPayload() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "AIEXTRACT_INVALID_WEBHOOK_PAYLOAD",
                "Webhook payload is invalid.");
    }

    record YoutubeAtomNotification(String channelId, String videoId, String videoUrl) {
    }
}
