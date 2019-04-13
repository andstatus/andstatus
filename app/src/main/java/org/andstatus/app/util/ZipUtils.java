/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.util;

import org.andstatus.app.context.MyStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.vavr.control.Try;

public class ZipUtils {

    private ZipUtils() { /* Empty */ }


    public static Try<File> zipFiles(File sourceFolder, File zipped) {
        try (FileOutputStream fos = new FileOutputStream(zipped);
            ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (File file : sourceFolder.listFiles()) {
                if (!file.isDirectory() && !MyStorage.isTempFile(file)) addToZip(file, zos);
            }
        } catch (IOException e) {
            return Try.failure(new IOException("Error zipping " + sourceFolder.getAbsolutePath() + " folder to " +
                    zipped.getAbsolutePath() + ", " + e.getMessage()));
        }
        return Try.success(zipped);
    }

    private static void addToZip(File file, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zipEntry.setTime(file.lastModified());
            zos.putNextEntry(zipEntry);

            byte[] bytes = new byte[MyStorage.FILE_CHUNK_SIZE];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
        } finally {
            zos.closeEntry();
        }
    }

    public static void unzipFiles(File zipped, File targetFolder) throws IOException {
        if (!targetFolder.exists() && !targetFolder.mkdir()) {
            throw new IOException("Couldn't create folder: '" + targetFolder.getAbsolutePath() + "'");
        }
        try(ZipFile zipFile = new ZipFile(zipped)) {
            Enumeration<?> enu = zipFile.entries();
            while (enu.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) enu.nextElement();
                File file = new File(targetFolder, zipEntry.getName());
                try (InputStream is = zipFile.getInputStream(zipEntry);
                     FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] bytes = new byte[MyStorage.FILE_CHUNK_SIZE];
                    int length;
                    while ((length = is.read(bytes)) >= 0) {
                        fos.write(bytes, 0, length);
                    }
                }
                file.setLastModified(zipEntry.getTime());
            }
        }
    }
}
