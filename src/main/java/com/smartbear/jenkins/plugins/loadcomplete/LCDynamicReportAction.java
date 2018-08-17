/*
 * The MIT License
 *
 * Copyright (c) 2018, SmartBear Software
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.smartbear.jenkins.plugins.loadcomplete;

import hudson.model.Action;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Igor Filin
 */
public class LCDynamicReportAction implements Action{

    private final static String DOWNLOAD_FILE_NAME = "Test";

    private final String baseReportsPath;

    LCDynamicReportAction(String baseReportsPath) {

        this.baseReportsPath = baseReportsPath;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (!req.getMethod().equals("GET")) {
            rsp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String path = req.getRestOfPath();

        if (path.length() == 0 || path.contains("..") || path.length() < 1) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // remove trailing slash
        path = path.substring(1);
        String[] parts = path.split("/");
        if (parts.length == 0) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String ext = null;
        if (parts.length == 1 && parts[0].endsWith(Constants.ZIP_FILE_EXTENSION)) {
            ext = Constants.ZIP_FILE_EXTENSION;
        } else if (parts.length == 1 && parts[0].endsWith(Constants.PDF_FILE_EXTENSION)) {
            ext = Constants.PDF_FILE_EXTENSION;
        } else if (parts.length == 1 && parts[0].endsWith(Constants.MHT_FILE_EXTENSION)) {
            ext = Constants.MHT_FILE_EXTENSION;
        }

        if (ext != null) {
            String requestedFilePath = baseReportsPath + parts[0];
            File file = new File(requestedFilePath);

            if (!file.exists() || !file.isFile() || !file.canRead()) {
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            FileInputStream fis = null;

            try {
                fis = new FileInputStream(file);
                rsp.setHeader("Content-Disposition", "filename=\"" + DOWNLOAD_FILE_NAME + ext + "\"");
                rsp.serveFile(req, fis, file.lastModified(), 0, file.length(), "mime-type:application/force-download");
            } catch (IOException e) {
                rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                if (fis != null)
                    fis.close();
            }
        } else {
            String archiveName = parts[0] + Constants.ZIP_FILE_EXTENSION;
            File logFile = new File(baseReportsPath, archiveName);
            if (!logFile.exists() || !logFile.isFile()) {
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String entryName;
            if (parts.length == 1) {
                entryName = "index.html";
            } else {
                entryName = path.substring(parts[0].length() + 1);
            }

            ZipFile archive = null;
            InputStream inputStream = null;
            try {
                archive = new ZipFile(logFile);
                ZipEntry targetEntry = searchEntry(archive, entryName);
                if (targetEntry == null) {
                    rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                inputStream = archive.getInputStream(targetEntry);

                // Override content-type for "data/report.data*" files (must be "application/javascript")
                if (entryName.startsWith("data/report.data")) {
                    rsp.serveFile(req, inputStream, targetEntry.getTime(), 0, targetEntry.getSize(), "report_data.js");
                } else {
                    rsp.serveFile(req, inputStream, targetEntry.getTime(), 0, targetEntry.getSize(), targetEntry.getName());
                }
            }
            catch (Exception e) {
                rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (archive != null) {
                    archive.close();
                }
            }
        }
    }

    private ZipEntry searchEntry(ZipFile archive, String entryName) {
        ZipEntry targetEntry = archive.getEntry(entryName);
        if (targetEntry == null) {
            entryName = entryName.replace("/", "\\");
            targetEntry = archive.getEntry(entryName);
        }

        if (targetEntry == null) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            entryName = entryName.toUpperCase();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String currentEntryName = entry.getName().toUpperCase();

                if (currentEntryName.equals(entryName) || currentEntryName.replace("/", "\\").equals(entryName)) {
                    return entry;
                }
            }
        }

        return targetEntry;
    }

}