package com.masiton.ai.presentation.webhook;

import java.io.ByteArrayInputStream;

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

    YoutubeAtomNotification parse(byte[] payload) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Document document = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(payload)));
            Element root = document.getDocumentElement();
            if (!"feed".equals(root.getLocalName()) || !ATOM_NAMESPACE.equals(root.getNamespaceURI())) {
                throw invalidPayload();
            }
            String channelId = requiredText(document, YOUTUBE_NAMESPACE, "channelId");
            String videoId = requiredText(document, YOUTUBE_NAMESPACE, "videoId");
            String videoUrl = requiredAlternateVideoUrl(document);
            return new YoutubeAtomNotification(channelId, videoId, videoUrl);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPayload();
        }
    }

    private String requiredText(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        if (nodes.getLength() != 1) {
            throw invalidPayload();
        }
        String value = nodes.item(0).getTextContent();
        if (value == null || value.isBlank()) {
            throw invalidPayload();
        }
        return value.trim();
    }

    private String requiredAlternateVideoUrl(Document document) {
        NodeList links = document.getElementsByTagNameNS(ATOM_NAMESPACE, "link");
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            if ("alternate".equals(link.getAttribute("rel"))) {
                String href = link.getAttribute("href");
                if (!href.isBlank()) {
                    return href.trim();
                }
            }
        }
        throw invalidPayload();
    }

    private BusinessException invalidPayload() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "AIEXTRACT_INVALID_WEBHOOK_PAYLOAD",
                "Webhook payload is invalid.");
    }

    record YoutubeAtomNotification(String channelId, String videoId, String videoUrl) {
    }
}
