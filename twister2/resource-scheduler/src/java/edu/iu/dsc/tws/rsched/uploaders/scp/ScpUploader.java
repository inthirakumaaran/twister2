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
package edu.iu.dsc.tws.rsched.uploaders.scp;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.rsched.spi.uploaders.IUploader;
import edu.iu.dsc.tws.rsched.spi.uploaders.UploaderException;


public class ScpUploader implements IUploader {
  private static final Logger LOG = Logger.getLogger(ScpUploader.class.getName());

  // this is the directory where to upload the file
  private String destinationDirectory;
  private ScpController controller;
  private Config config;

  protected ScpController getScpController() {
    String scpOptions = ScpContext.scpOptions(config);
    String scpConnection = ScpContext.scpConnection(config);
    String sshOptions = ScpContext.sshOptions(config);
    String sshConnection = ScpContext.sshConnection(config);

    if (scpOptions == null) {
      throw new RuntimeException("Missing "
          + ScpContext.TWISTER2_UPLOADER_SCP_OPTIONS + " config value");

    }
    if (scpConnection == null) {
      throw new RuntimeException("Missing "
          + ScpContext.TWISTER2_UPLOADER_SCP_CONNECTION + " config value");
    }

    if (sshOptions == null) {
      throw new RuntimeException("Missing "
          + ScpContext.TWISTER2_UPLOADER_SSH_OPTIONS + " config value");
    }
    if (sshConnection == null) {
      throw new RuntimeException("Missing "
          + ScpContext.TWISTER2_UPLOADER_SSH_CONNECTION + " config value");
    }

    return new ScpController(
        scpOptions, scpConnection, sshOptions, sshConnection);
  }


  @Override
  public void initialize(Config cfg) {
    this.config = cfg;
    this.controller = getScpController();
    this.destinationDirectory = ScpContext.uploaderJobDirectory(config);
  }

  @Override
  public URI uploadPackage(String sourceLocation) throws UploaderException {

    File file = new File(sourceLocation);
    String fileName = file.getName();
    boolean fileExists = file.isFile();
    if (!fileExists) {
      throw new UploaderException(
          String.format("Job package does not exist at '%s'", sourceLocation));
    }

    if (!this.controller.mkdirsIfNotExists(destinationDirectory)) {
      throw new UploaderException(
          String.format(
              "Failed to create directories required for uploading the topology %s",
              destinationDirectory));
    }

    LOG.log(Level.INFO, String.format(
        "Uploading the file from local file system to remote machine: %s -> %s.",
        sourceLocation, destinationDirectory));
    try {
      if (!this.controller.copyFromLocalFile(sourceLocation, destinationDirectory)) {
        throw new UploaderException(
            String.format(
                "Failed to upload the file from local file system to remote machine: %s -> %s.",
                sourceLocation, destinationDirectory));
      }
      LOG.log(Level.INFO, String.format("Uploaded to remote machine: %s -> %s.",
          sourceLocation, destinationDirectory));
      return new URI(destinationDirectory + "/" + fileName);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Invalid file path for topology package destination: "
          + destinationDirectory, e);
    }
  }

  @Override
  public boolean undo() {
    LOG.info("Clean uploaded jar");
    File file = new File(destinationDirectory);
    return file.delete();
  }

  @Override
  public void close() {

  }
}
