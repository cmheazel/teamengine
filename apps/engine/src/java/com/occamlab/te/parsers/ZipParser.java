package com.occamlab.te.parsers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.InputStream;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.http.HttpMessage;
import org.apache.http.message.BasicHttpResponse;

import com.occamlab.te.TECore;

/**
 * Parses a zip file input by extracting the contents into the directory
 * specified by the value of the <code>java.io.tmpdir</code> system property.
 * The resulting manifest is structured as follows:
 *
 * <ctl:manifest xmlns:ctl="http://www.occamlab.com/ctl"> <ctl:file-entry
 * full-path="${java.io.tmpdir}/dir/doc.kml" size="2048" /> </ctl:manifest>
 *
 * @author jparrpearson
 */
public class ZipParser {

    public static final String PARSERS_NS = "http://www.occamlab.com/te/parsers";

    public static final String CTL_NS = "http://www.occamlab.com/ctl";

    // Add more mime types as necessary, if mime type not listed a default will
    // be given
    public static String[][] ApplicationMediaTypeMappings = {
            { "kml", "vnd.google-earth.kml+xml" },
            { "kmz", "vnd.google-earth.kmz" }, { "xml", "application/xml" },
            { "txt", "text/plain" }, { "jpg", "image/jpeg" },
            { "jpeg", "image/jpeg" }, { "gif", "image/gif" },
            { "png", "image/png" } };

    /**
     * Returns the mime media type value for the given extension
     *
     * @param ext
     *            the filename extension to lookup
     * @return String the mime type for the given extension
     */
    public static String getMediaType(String ext) {
        String mediaType = "";

        // Find the media type value in the lookup table
        for (int i = 0; i < ApplicationMediaTypeMappings.length; i++) {
            if (ApplicationMediaTypeMappings[i][0].equals(ext.toLowerCase())) {
                mediaType = ApplicationMediaTypeMappings[i][1];
            }
        }

        // Give the media type default of "application/octet-stream"
        if (mediaType.equals("")) {
            mediaType = "application/octet-stream";
        }

        return mediaType;
    }

    /**
     * Parses the entity (a ZIP archive) obtained in response to submitting a
     * request to some URL. The resulting manifest is an XML document with
     * <ctl:manifest> as the document element.
     *
     * @paramisc
     *            a stream from the remote connection
     * @param instruction
     *            a DOM Element representation of configuration information for
     *            this parser
     * @param logger
     *            the test logger
     * @param core
     *            the test harness
     * @return a DOM Document representing the manifest of items in the archive.
     * @throws Throwable
     */
    public static Document parse(HttpMessage msg, Element instruction,
            PrintWriter logger, TECore core) throws Throwable {

	InputStream is = ((BasicHttpResponse) msg).getEntity().getContent();

        // Create the response element, <ctl:manifest>
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        Element root = doc.createElementNS(CTL_NS, "manifest");

        // Open the connection to the zip file
        ZipInputStream zis = new ZipInputStream(is);

        // Create the full directory path to store the zip entities
        Document d = instruction.getOwnerDocument();
        NodeList nodes = d.getElementsByTagNameNS(CTL_NS, "SessionDir");
        // Either use the given session directory, or make one in the java temp
        // directory
        String path = "";
        if (nodes.getLength() > 0) {
            Element e = (Element) nodes.item(0);
            path = e.getTextContent();
        } else {
            path = System.getProperty("java.io.tmpdir") + "/zipparser.temp";
        }
        String randomStr = TECore.randomString(16, new Random());
        path = path + "/work/" + randomStr;
        new File(path).mkdirs();

        // Unzip the file to a temporary location (java temp)
        ZipEntry entry = null;
        while ((entry = zis.getNextEntry()) != null) {
            // Open the output file and get info from it
            String filename = entry.getName();
            long size = entry.getSize();
            String ext = filename.substring(filename.lastIndexOf(".") + 1);
            String mediaType = getMediaType(ext);
            // Make the temp directory and subdirectories if needed
            String subdir = "";
            if (filename.lastIndexOf("/") != -1)
                subdir = filename.substring(0, filename.lastIndexOf("/"));
            else if (filename.lastIndexOf("\\") != -1)
                subdir = filename.substring(0, filename.lastIndexOf("\\"));
            new File(path + "/" + subdir).mkdir();
            File outFile = new File(path, filename);
            if (outFile.isDirectory())
                continue;
            OutputStream out = new FileOutputStream(outFile);

            // Transfer bytes from the ZIP file to the output file
            byte[] buf = new byte[1024];
            int len;
            while ((len = zis.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

            // Add the file information to the document
            Element fileEntry = doc.createElementNS(CTL_NS, "file-entry");
            fileEntry.setAttribute("full-path", outFile.getPath().replace('\\',
                    '/'));
            fileEntry.setAttribute("media-type", mediaType);
            fileEntry.setAttribute("size", String.valueOf(size));
            root.appendChild(fileEntry);
        }

        doc.appendChild(root);

        // Return the <ctl:manifest> document
        return doc;
    }

}
