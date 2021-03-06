//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.rsched.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.rsched.core.SchedulerContext;

public final class TarGzipPacker {
  private TarArchiveOutputStream tarOutputStream;
  private Path archiveFile;
  private static final String DIR_PREFIX_FOR_ARCHIVE = "./twister2-core/";

  /**
   * Pack
   * @param archiveFile
   * @param tarOutputStream
   */
  private TarGzipPacker(Path archiveFile, TarArchiveOutputStream tarOutputStream) {
    this.archiveFile = archiveFile;
    this.tarOutputStream = tarOutputStream;
  }

  /**
   * Create
   * @param targetDir
   * @return
   */
  public static TarGzipPacker createTarGzipPacker(String targetDir, Config config) {
    // this should be received from config
    String archiveFilename = SchedulerContext.jobPackageName(config);
    Path archiveFile = Paths.get(targetDir + "/" + archiveFilename);

    try {
      // construct output stream
      OutputStream outStream = Files.newOutputStream(archiveFile);
      GzipCompressorOutputStream gzipOutputStream = new GzipCompressorOutputStream(outStream);
      TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(gzipOutputStream);

      return new TarGzipPacker(archiveFile, tarOutputStream);
    } catch (IOException ioe) {
      System.out.println("can not create archive file");
      ioe.printStackTrace();
      return null;
    }
  }

  /**
   * given tar.gz file will be copied to this tar.gz file.
   * all files will be transferred to new tar.gz file one by one.
   * original directory structure will be kept intact
   *
   * @param tarGzipFile the archive file to be copied to the new archive
   */
  public void addTarGzipToArchive(String tarGzipFile) {
    try {
      // construct input stream
      InputStream fin = Files.newInputStream(Paths.get(tarGzipFile));
      BufferedInputStream in = new BufferedInputStream(fin);
      GzipCompressorInputStream gzIn = new GzipCompressorInputStream(in);
      TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzIn);

      // copy the existing entries from source gzip file
      ArchiveEntry nextEntry;
      while ((nextEntry = tarInputStream.getNextEntry()) != null) {
        tarOutputStream.putArchiveEntry(nextEntry);
        IOUtils.copy(tarInputStream, tarOutputStream);
        tarOutputStream.closeArchiveEntry();
      }

      tarInputStream.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

  /**
   * add one file to tar.gz file
   *
   * @param filename full path file name to be added to the jar
   */
  public void addFileToArchive(String filename) {
    File file = new File(filename);
    addFileToArchive(file, DIR_PREFIX_FOR_ARCHIVE);
  }

  /**
   * add one file to tar.gz file
   *
   * @param file file to be added to the tar.gz
   */
  public void addFileToArchive(File file, String dirPrefixForTar) {
    try {
      String filePathInTar = dirPrefixForTar + file.getName();

      TarArchiveEntry entry = new TarArchiveEntry(file, filePathInTar);
      entry.setSize(file.length());
      tarOutputStream.putArchiveEntry(entry);
      IOUtils.copy(new FileInputStream(file), tarOutputStream);
      tarOutputStream.closeArchiveEntry();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * add all files in the given directory to the tar.gz file
   * add the given prefix to all files in tar names
   * do not copy files recursively. Only one level copying.
   *
   * @param path of the firectory to be added
   */
  public void addDirectoryToArchive(String path) {

    File dir = new File(path);

    String prefix = DIR_PREFIX_FOR_ARCHIVE + dir.getName() + "/";
    for (File file : dir.listFiles()) {
      addFileToArchive(file, prefix);
    }
  }

  /**
   * close the tar stream
   */
  public void close() {
    try {
      this.tarOutputStream.finish();
      this.tarOutputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
